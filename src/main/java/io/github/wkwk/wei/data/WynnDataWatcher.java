package io.github.wkwk.wei.data;

import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.WeiConfig;

/**
 * Wynntils のデータと表示条件を監視し、必要なときだけカタログを作り直して EMI を再読み込みする。
 *
 * <ul>
 *   <li>データの変化: カタログを作り直してから EMI を再読み込み。
 *       きっかけは Wynntils の取得完了イベント。件数の監視は取りこぼし対策。</li>
 *   <li>表示条件（設定・接続状態）だけの変化: EMI だけ再読み込み。</li>
 *   <li>両方が変わっても再読み込みは作り直し後の 1 回。</li>
 *   <li>一部の種類が失敗したら間隔を広げて再試行する（読めた分は表示）。</li>
 *   <li>作り直しは 1 ティック数ミリ秒ずつ進める。</li>
 * </ul>
 *
 * クライアントスレッドで動く。
 */
public final class WynnDataWatcher {
    private static final int CHECK_INTERVAL_TICKS = 20;
    /** 件数が 2 回続けて同じになってから作り直す（取得途中で作らないため） */
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
    // EMI の読み込みスレッドから読む
    private static volatile boolean buildInProgress;
    private static volatile boolean catalogBuiltOnce;
    private static String buildTargetSignature;
    private static int failedAttempts;
    private static long retryAtMillis = -1;

    // 表示条件
    private static volatile String appliedViewSignature = "";
    private static volatile boolean onWynncraft;

    // diag#3 / #5 / #9
    private static String lastLoggedDataSignature;
    private static final StringBuilder emiReloadReasons = new StringBuilder();
    private static long buildStartNanos;
    private static int buildTicks;
    private static double buildMaxStepMs;
    private static String buildReason = "";
    private static int favoritesRevisionAtBuild = Integer.MIN_VALUE;
    private static int lastLoggedFavoritesRevision = Integer.MIN_VALUE;

    // EMI が最後に登録に使ったカタログと表示条件
    private static boolean emiReloadPending;
    private static volatile boolean emiHasRegistered;
    private static volatile WynnCatalog registeredCatalog;
    private static volatile String registeredViewSignature;

    private WynnDataWatcher() {}

    public static void tick() {
        if (build != null) {
            stepBuild();
        }

        if (++ticks % CHECK_INTERVAL_TICKS == 0) {
            checkData();
            checkView();
        }

        // 再読み込みは作り直し完了後に 1 回。EMI の読み込み中は保留
        // （ロードゲートで一覧の完成を待つため、読み込み後は最新）
        boolean emiLoading = false;
        try {
            emiLoading = dev.emi.emi.runtime.EmiReloadManager.getStatus() == 1;
        } catch (Throwable ignored) {
        }
        if (emiReloadPending && build == null && !emiLoading) {
            emiReloadPending = false;
            // 未読み込み、または EMI 自身の読み込みで最新を拾っていれば再読み込みしない
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
            // データが変われば失敗回数をリセット
            failedAttempts = 0;
            retryAtMillis = -1;
        }
        if (rebuildRequested || settled || retryDue) {
            // 受信直後は Wynntils のガイド用アイテムの説明文が空になることがある。
            // 装備 1 件で説明文が作れるまで待つ（最大 30 秒）
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

    /** diag#9: Wynntils のお気に入りが変わると、構築時の説明文の星が古くなる */
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
                // diag#3/#4: 予算超過（カクつきの可能性）
                Diag.warn(3, "Catalog build step took {}ms (budget 4ms)", stepMs);
            }
        } catch (Throwable t) {
            // 個々の作業の例外は Build 内で処理済み。ここは想定外
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
        // 不完全でも記録する（毎秒作り直さないため）。不完全なら再試行を予約
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
        Diag.warn(5, "Retry #{} scheduled in {}s (each retry rebuilds everything and reloads EMI)", failedAttempts, delay / 1000);
        retryAtMillis = System.currentTimeMillis() + delay;
        Wei.LOGGER.info("Retrying the Wynncraft item catalog in {} s", delay / 1000);
    }

    /** Wynntils の取得完了（一部失敗を含む）。次の確認で作り直す。 */
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

    /** /wei reload。EMI だけ再読み込み。 */
    public static void requestEmiReload() {
        addEmiReloadReason("command");
        boolean wynn = WynnCatalog.isOnWynncraft();
        onWynncraft = wynn;
        appliedViewSignature = wynn + "|" + WeiConfig.currentSignature();
        emiReloadPending = true;
        // 設定が同じでも再読み込みする
        registeredViewSignature = null;
    }

    /**
     * ロードゲート（EMI の読み込みスレッドから呼ばれる）。
     * 一度作り終えていて、作り直し中でなければ true。
     */
    public static boolean isCatalogReadyForEmi() {
        return catalogBuiltOnce && !buildInProgress;
    }

    public static boolean onWynncraft() {
        return onWynncraft;
    }

    /** 表示条件の指紋 */
    public static String viewSignature() {
        return appliedViewSignature;
    }

    /** 登録に使った内容を記録する（EMI のスレッドから呼ばれる） */
    public static void markEmiRegistered(WynnCatalog catalog, String viewSignature) {
        Diag.log(3, "EMI registered WEI: catalog={} items (current={}), view='{}' (applied='{}'), thread={}",
                catalog.entries().size(), catalog == WynnCatalog.current(), viewSignature, appliedViewSignature, Diag.thread());
        registeredCatalog = catalog;
        registeredViewSignature = viewSignature;
        emiHasRegistered = true;
    }
}
