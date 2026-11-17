package io.github.wkwk.wynnemi.data;

import io.github.wkwk.wynnemi.WynnEmiClient;
import io.github.wkwk.wynnemi.WynnEmiConfig;
import io.github.wkwk.wynnemi.emi.EmiReloadHelper;

/**
 * Wynntils のデータは起動後に非同期でダウンロードされ、EMI の初回読み込みに間に合わないことがある。
 * 1秒ごとにデータの量（と Wynncraft 接続状態、設定）を確認し、変化して2秒落ち着いたら
 * カタログを作り直して EMI を再読み込みする。
 */
public final class WynnDataWatcher {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int STABLE_CHECKS_REQUIRED = 2;

    private static int ticks;
    private static String builtSignature;
    private static String pendingSignature;
    private static int stableChecks;
    private static boolean reloadRequested;
    private static volatile boolean emiHasRegistered;

    private WynnDataWatcher() {}

    public static void tick() {
        if (++ticks % CHECK_INTERVAL_TICKS != 0) return;

        String signature = WynnCatalog.computeSignature() + "|" + WynnEmiConfig.currentSignature();

        if (reloadRequested) {
            reloadRequested = false;
            rebuild(signature);
            return;
        }
        if (signature.equals(builtSignature)) {
            pendingSignature = null;
            return;
        }
        if (!signature.equals(pendingSignature)) {
            pendingSignature = signature;
            stableChecks = 0;
            return;
        }
        if (++stableChecks >= STABLE_CHECKS_REQUIRED) {
            rebuild(signature);
        }
    }

    private static void rebuild(String signature) {
        try {
            WynnCatalog.rebuild();
        } catch (Throwable t) {
            WynnEmiClient.LOGGER.error("Failed to build Wynncraft item catalog", t);
        }
        builtSignature = signature;
        pendingSignature = null;
        // EMI がまだ一度も読み込んでいなければ、初回読み込みでそのまま拾われる
        if (emiHasRegistered) {
            EmiReloadHelper.reload();
        }
    }

    public static void requestReload() {
        reloadRequested = true;
    }

    public static void markEmiRegistered() {
        emiHasRegistered = true;
    }
}
