package io.github.wkwk.wei.data;

import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.WeiConfig;

/**
 * Wynntils のデータと表示条件を見張り、必要なときだけカタログを作り直して EMI を再読み込みする。
 *
 * <ul>
 *   <li><b>データ</b>（アイテム情報）が変わったら、カタログを作り直してから EMI を再読み込み。
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
    // EMI の読み込みスレッドから読む（ロードゲート用）
    private static volatile boolean buildInProgress;
    private static volatile boolean catalogBuiltOnce;
    private static String buildTargetSignature;
    private static int failedAttempts;
    private static long retryAtMillis = -1;

    // 表示条件
    private static volatile String appliedViewSignature = "";
    private static volatile boolean onWynncraft;

    // 診断用（diag#3 / #5 / #9）
    private static String lastLoggedDataSignature;
    private static final StringBuilder emiReloadReasons = new StringBuilder();
    private static long buildStartNanos;
    private static int buildTicks;
    private static double buildMaxStepMs;
    private static String buildReason = "";
    private static int favoritesRevisionAtBuild = Integer.MIN_VALUE;
    private static int lastLoggedFavoritesRevision = Integer.MIN_VALUE;

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
        // EMI が読み込み中なら判断を保留する。ロードゲートで一覧の完成を待っているので、
        // 読み込みが終われば最新の一覧を持っている（重ねて再読み込みしない）
        boolean emiLoading = false;
        try {
            emiLoading = dev.emi.emi.runtime.EmiReloadManager.getStatus() == 1;
        } catch (Throwable ignored) {
        }
        if (emiReloadPending && build == null && !emiLoading) {
            emiReloadPending = false;
            // EMI がまだ読み込んでいない、または EMI 自身の再読み込み（ワールド参加時など）で
            // すでに最新の内容を拾っていれば、重ねて再読み込みしない
            boolean catalogStale = registeredCatalog != WynnCatalog.current();
            boolean viewStale = !appliedViewSignature.equals(registeredViewSignature);
            boolean stale = catalogStale || viewStale;
            String reasons = emiReloadReasons.toString();
            emiReloadReasons.setLength(0);
            if (emiHasRegistered && stale) {
                Diag.log(3, "WEI triggers EMI reload: reasons=[{}] catalogStale={} viewStale={} view='{}' registeredView='{}'",
                        reasons, catalogStale, viewStale, appliedViewSignature, registeredViewSignature);
                dev.emi.emi.runtime.EmiReloadManager.reload();
            } else {
                Diag.log(3, "EMI reload skipped: reasons=[{}] emiHasRegistered={} stale={} ({})",
                        reasons, emiHasRegistered, stale,
                        emiHasRegistered ? "EMI already has the latest" : "EMI has not loaded yet; it will pick up the latest when it does");
            }
        }
    }

    private static final long TOOLTIP_WAIT_MAX_MS = 30_000;
    private static long tooltipWaitSince = -1;

    private static void checkData() {
        String data = WynnCatalog.computeDataSignature();
        if (!data.equals(lastLoggedDataSignature)) {
            // diag#3
            Diag.log(3, "Data signature changed: '{}' -> '{}'", lastLoggedDataSignature, data);
            lastLoggedDataSignature = data;
        }
        checkFavoritesRevision();

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
            // Wynntils がデータを受け取った直後は、ガイド用アイテムの説明文が空で返ってくることがある（実機ログで先頭 80 件）。
            // 装備 1 件で説明文が作れるようになるまで作り始めない（最大 30 秒。過ぎたら作る）
            if (!WynnCatalog.tooltipsReady()) {
                if (tooltipWaitSince < 0) {
                    tooltipWaitSince = System.currentTimeMillis();
                    Diag.log(3, "Waiting for Wynntils tooltips to become ready before building the catalog");
                }
                if (System.currentTimeMillis() - tooltipWaitSince < TOOLTIP_WAIT_MAX_MS) return;
                Wei.LOGGER.warn("Wynntils tooltips are still empty after {}s; building the item list anyway",
                        TOOLTIP_WAIT_MAX_MS / 1000);
            }
            if (tooltipWaitSince >= 0) {
                Diag.log(3, "Tooltips ready after {}ms", System.currentTimeMillis() - tooltipWaitSince);
                tooltipWaitSince = -1;
            }
            buildReason = (rebuildRequested ? "requested(event/command) " : "") + (settled ? "dataChanged " : "")
                    + (retryDue ? "retry#" + failedAttempts : "");
            rebuildRequested = false;
            retryAtMillis = -1;
            startBuild(data);
        }
    }

    private static void checkView() {
        boolean wynn = WynnCatalog.isOnWynncraft();
        String view = wynn + "|" + WeiConfig.currentSignature();
        if (view.equals(appliedViewSignature)) return;

        Diag.log(3, "View changed: '{}' -> '{}' (onWynncraft={}, emiHasRegistered={}, registeredView='{}')",
                appliedViewSignature, view, wynn, emiHasRegistered, registeredViewSignature);
        onWynncraft = wynn;
        appliedViewSignature = view;
        addEmiReloadReason("view");
        emiReloadPending = true;
    }

    private static void addEmiReloadReason(String reason) {
        if (!emiReloadReasons.isEmpty()) emiReloadReasons.append(',');
        emiReloadReasons.append(reason);
    }

    /** diag#9: 説明文は構築時のまま。Wynntils のお気に入りが変わったら、星などが古くなっている */
    private static void checkFavoritesRevision() {
        if (!Diag.enabled()) return;
        try {
            int revision = com.wynntils.core.components.Services.Favorites.getRevision();
            if (revision != lastLoggedFavoritesRevision) {
                if (favoritesRevisionAtBuild != Integer.MIN_VALUE && revision != favoritesRevisionAtBuild) {
                    Diag.log(9, "Wynntils favorites changed after the catalog was built (revision {} -> {}): tooltips may be stale",
                            favoritesRevisionAtBuild, revision);
                }
                lastLoggedFavoritesRevision = revision;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void startBuild(String targetSignature) {
        Diag.log(3, "Catalog build START: reason=[{}] target='{}'", buildReason, targetSignature);
        buildStartNanos = System.nanoTime();
        buildTicks = 0;
        buildMaxStepMs = 0;
        build = new WynnCatalog.Build();
        buildInProgress = true;
        buildTargetSignature = targetSignature;
        pendingDataSignature = null;
        stableChecks = 0;
    }

    private static void stepBuild() {
        boolean done;
        long stepStart = System.nanoTime();
        try {
            done = build.step(System.nanoTime() + BUILD_BUDGET_NANOS);
            buildTicks++;
            double stepMs = Diag.ms(stepStart);
            if (stepMs > buildMaxStepMs) buildMaxStepMs = stepMs;
            if (stepMs > 20) {
                // diag#3/#4: 1 ティックの作業が予定（4ms）を大きく超えた＝カクつきの可能性
                Diag.warn(3, "Catalog build step took {}ms (budget 4ms)", stepMs);
            }
        } catch (Throwable t) {
            // 個々の作業は Build 内で捕まえているので、ここに来るのは想定外
            Wei.LOGGER.error("Wynncraft item catalog build crashed", t);
            build = null;
            buildInProgress = false;
            catalogBuiltOnce = true;
            scheduleRetry();
            return;
        }
        if (!done) return;

        WynnCatalog.Build finished = build;
        WynnCatalog.publish(finished.result());
        build = null;
        catalogBuiltOnce = true;
        buildInProgress = false;
        // 不完全でも記録する（件数の監視で毎秒作り直さないため）。不完全なら下で再試行を予約する
        builtDataSignature = buildTargetSignature;
        addEmiReloadReason("catalogBuilt");
        emiReloadPending = true;
        Diag.log(3, "Catalog build DONE: {} items, complete={}, {}ms over {} ticks, max step {}ms, {}",
                finished.size(), finished.complete(), Diag.ms(buildStartNanos), buildTicks, buildMaxStepMs,
                finished.slowTaskSummary());
        try {
            favoritesRevisionAtBuild = com.wynntils.core.components.Services.Favorites.getRevision();
        } catch (Throwable ignored) {
        }

        if (finished.complete()) {
            failedAttempts = 0;
            retryAtMillis = -1;
            Wei.LOGGER.info("Built Wynncraft item catalog: {} items", finished.size());
        } else {
            Wei.LOGGER.warn("Built Wynncraft item catalog with missing groups {}: {} items",
                    finished.failedGroups(), finished.size());
            scheduleRetry();
        }
    }

    private static void scheduleRetry() {
        if (failedAttempts >= RETRY_DELAYS_MS.length) {
            Wei.LOGGER.warn("Giving up retrying the Wynncraft item catalog until data changes or /wei rebuild");
            retryAtMillis = -1;
            return;
        }
        long delay = RETRY_DELAYS_MS[failedAttempts++];
        // diag#5: 再試行のたびに全体の作り直しと EMI の再読み込みが起きる
        Diag.warn(5, "Retry #{} scheduled in {}s (each retry rebuilds everything and reloads EMI)", failedAttempts, delay / 1000);
        retryAtMillis = System.currentTimeMillis() + delay;
        Wei.LOGGER.info("Retrying the Wynncraft item catalog in {} s", delay / 1000);
    }

    /** Wynntils のダウンロード完了（または一部失敗）時。次の確認で作り直す。 */
    public static void onWynntilsDataDownloaded() {
        Diag.log(3, "Wynntils download event received -> rebuild requested");
        failedAttempts = 0;
        rebuildRequested = true;
    }

    /** /wei rebuild */
    public static void requestRebuild() {
        failedAttempts = 0;
        rebuildRequested = true;
    }

    /** /wei reload（設定を読み直した後）。カタログは作り直さず EMI だけ再読み込み。 */
    public static void requestEmiReload() {
        addEmiReloadReason("command");
        boolean wynn = WynnCatalog.isOnWynncraft();
        onWynncraft = wynn;
        appliedViewSignature = wynn + "|" + WeiConfig.currentSignature();
        emiReloadPending = true;
        // 設定が同じでも、明示的に頼まれたら必ず再読み込みする
        registeredViewSignature = null;
    }

    /**
     * EMI のロードゲート用（EMI の読み込みスレッドから呼ばれる）。
     * 一覧を一度作り終えていて、作り直しの途中でなければ true。
     * これが false の間に EMI が読み込むと、WEI のお気に入りや履歴が読めずに外れてしまう（diag#1）。
     */
    public static boolean isCatalogReadyForEmi() {
        return catalogBuiltOnce && !buildInProgress;
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
        Diag.log(3, "EMI registered WEI: catalog={} items (current={}), view='{}' (applied='{}'), thread={}",
                catalog.entries().size(), catalog == WynnCatalog.current(), viewSignature, appliedViewSignature, Diag.thread());
        registeredCatalog = catalog;
        registeredViewSignature = viewSignature;
        emiHasRegistered = true;
    }
}
