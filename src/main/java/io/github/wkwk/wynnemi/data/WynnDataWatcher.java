package io.github.wkwk.wynnemi.data;

import io.github.wkwk.wynnemi.WynnEmiClient;
import io.github.wkwk.wynnemi.WynnEmiConfig;
import io.github.wkwk.wynnemi.emi.EmiReloadHelper;

/**
 * Wynntils のデータと表示条件を見張り、必要なときだけカタログを作り直して EMI を再読み込みする。
 *
 * <ul>
 *   <li><b>データ</b>（アイテム情報・プレイヤーのクラス）が変わったら、カタログを作り直してから EMI を再読み込み。
 *       主なきっかけは Wynntils のダウンロード完了イベント。件数の確認は初回の取りこぼし防止の保険。</li>
 *   <li><b>表示条件</b>（設定・Wynncraft 接続状態）だけが変わったら、カタログは作り直さず EMI だけ再読み込み。</li>
 *   <li>両方が同時に変わっても、EMI の再読み込みは作り直しが終わった後の1回だけ。</li>
 *   <li>一部の種類の読み込みに失敗したら、間隔を広げながら上限回数まで作り直しを再試行する。
 *       その間も読めた分は表示する。</li>
 *   <li>作り直しは1ティックあたり数ミリ秒ずつ進め、ゲームが止まらないようにする。</li>
 * </ul>
 *
 * すべてクライアントスレッドで動く。
 */
public final class WynnDataWatcher {
    private static final int CHECK_INTERVAL_TICKS = 20;
    /** 件数の変化は、2回続けて同じ値になってから作り直す（ダウンロード途中で作らないため） */
    private static final int STABLE_CHECKS_REQUIRED = 2;
    private static final long BUILD_BUDGET_NANOS = 4_000_000L;
    private static final long[] RETRY_DELAYS_MS = {5_000, 15_000, 60_000, 300_000};

    private static int ticks;

    // データ
    private static String builtDataSignature;
    private static String pendingDataSignature;
    private static int stableChecks;
    private static boolean rebuildRequested;
    private static WynnCatalog.Build build;
    private static String buildTargetSignature;
    private static int failedAttempts;
    private static long retryAtMillis = -1;

    // 表示条件
    private static volatile String appliedViewSignature = "";
    private static volatile boolean onWynncraft;

    // EMI（最後に EMI が読み込んだときに使ったカタログと表示条件）
    private static boolean emiReloadPending;
    private static volatile boolean emiHasRegistered;
    private static volatile WynnCatalog registeredCatalog;
    private static volatile String registeredViewSignature;

    private WynnDataWatcher() {}

    public static void tick() {
        // 作り直しの途中なら少しずつ進める
        if (build != null) {
            stepBuild();
        }

        if (++ticks % CHECK_INTERVAL_TICKS == 0) {
            checkData();
            checkView();
        }

        // EMI の再読み込みは、作り直しが終わってから1回だけ
        if (emiReloadPending && build == null) {
            emiReloadPending = false;
            // EMI がまだ読み込んでいない、または EMI 自身の再読み込み（ワールド参加時など）で
            // すでに最新の内容を拾っていれば、重ねて再読み込みしない
            boolean stale = registeredCatalog != WynnCatalog.current()
                    || !appliedViewSignature.equals(registeredViewSignature);
            if (emiHasRegistered && stale) {
                EmiReloadHelper.reload();
            }
        }
    }

    private static void checkData() {
        String data = WynnCatalog.computeDataSignature();

        boolean changed = !data.equals(builtDataSignature);
        if (changed && !data.equals(pendingDataSignature)) {
            pendingDataSignature = data;
            stableChecks = 0;
        }
        boolean settled = changed && ++stableChecks >= STABLE_CHECKS_REQUIRED;
        boolean retryDue = retryAtMillis >= 0 && System.currentTimeMillis() >= retryAtMillis;

        if (build != null) return;
        if (settled) {
            // データ自体が変わったら、失敗回数は数え直す
            failedAttempts = 0;
            retryAtMillis = -1;
        }
        if (rebuildRequested || settled || retryDue) {
            rebuildRequested = false;
            retryAtMillis = -1;
            startBuild(data);
        }
    }

    private static void checkView() {
        boolean wynn = WynnCatalog.isOnWynncraft();
        String view = wynn + "|" + WynnEmiConfig.currentSignature();
        if (view.equals(appliedViewSignature)) return;

        onWynncraft = wynn;
        appliedViewSignature = view;
        emiReloadPending = true;
    }

    private static void startBuild(String targetSignature) {
        build = new WynnCatalog.Build();
        buildTargetSignature = targetSignature;
        pendingDataSignature = null;
        stableChecks = 0;
    }

    private static void stepBuild() {
        boolean done;
        try {
            done = build.step(System.nanoTime() + BUILD_BUDGET_NANOS);
        } catch (Throwable t) {
            // 個々の作業は Build 内で捕まえているので、ここに来るのは想定外
            WynnEmiClient.LOGGER.error("Wynncraft item catalog build crashed", t);
            build = null;
            scheduleRetry();
            return;
        }
        if (!done) return;

        WynnCatalog.Build finished = build;
        build = null;
        WynnCatalog.publish(finished.result());
        // 不完全でも記録する（件数の監視で毎秒作り直さないため）。不完全なら下で再試行を予約する
        builtDataSignature = buildTargetSignature;
        emiReloadPending = true;

        if (finished.complete()) {
            failedAttempts = 0;
            retryAtMillis = -1;
            WynnEmiClient.LOGGER.info("Built Wynncraft item catalog: {} items", finished.size());
        } else {
            WynnEmiClient.LOGGER.warn("Built Wynncraft item catalog with missing groups {}: {} items",
                    finished.failedGroups(), finished.size());
            scheduleRetry();
        }
    }

    private static void scheduleRetry() {
        if (failedAttempts >= RETRY_DELAYS_MS.length) {
            WynnEmiClient.LOGGER.warn("Giving up retrying the Wynncraft item catalog until data changes or /wynnemi rebuild");
            retryAtMillis = -1;
            return;
        }
        long delay = RETRY_DELAYS_MS[failedAttempts++];
        retryAtMillis = System.currentTimeMillis() + delay;
        WynnEmiClient.LOGGER.info("Retrying the Wynncraft item catalog in {} s", delay / 1000);
    }

    /** Wynntils のダウンロード完了（または一部失敗）時。次の確認で作り直す。 */
    public static void onWynntilsDataDownloaded() {
        failedAttempts = 0;
        rebuildRequested = true;
    }

    /** /wynnemi rebuild */
    public static void requestRebuild() {
        failedAttempts = 0;
        rebuildRequested = true;
    }

    /** /wynnemi reload（設定を読み直した後）。カタログは作り直さず EMI だけ再読み込み。 */
    public static void requestEmiReload() {
        boolean wynn = WynnCatalog.isOnWynncraft();
        onWynncraft = wynn;
        appliedViewSignature = wynn + "|" + WynnEmiConfig.currentSignature();
        emiReloadPending = true;
        // 設定が同じでも、明示的に頼まれたら必ず再読み込みする
        registeredViewSignature = null;
    }

    public static boolean onWynncraft() {
        return onWynncraft;
    }

    /** 表示条件の指紋（EMI のプラグインが読み込み時に記録する） */
    public static String viewSignature() {
        return appliedViewSignature;
    }

    /** EMI のプラグインが登録に使った内容を記録する（EMI のスレッドから呼ばれる） */
    public static void markEmiRegistered(WynnCatalog catalog, String viewSignature) {
        registeredCatalog = catalog;
        registeredViewSignature = viewSignature;
        emiHasRegistered = true;
    }
}
