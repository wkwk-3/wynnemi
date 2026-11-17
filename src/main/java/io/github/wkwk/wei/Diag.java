package io.github.wkwk.wei;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 実機テスト用の診断ログ。行の先頭に {@code [WEI diag#番号]} を付けるので、番号で絞り込める。
 * 番号はコードレビューで挙げた問題の番号（1〜11）に対応する。
 *
 * <p>config/wei.json の {@code diagnostics} を false にするか、JVM 引数 {@code -Dwei.diag=false} で止まる。
 */
public final class Diag {
    private static final boolean SYSTEM_ENABLED = !"false".equalsIgnoreCase(System.getProperty("wei.diag"));
    private static final Set<String> ONCE = ConcurrentHashMap.newKeySet();

    private Diag() {}

    public static boolean enabled() {
        return SYSTEM_ENABLED && WeiConfig.get().diagnostics;
    }

    public static void log(int issue, String message, Object... args) {
        if (enabled()) Wei.LOGGER.info("[WEI diag#" + issue + "] " + message, args);
    }

    public static void warn(int issue, String message, Object... args) {
        if (enabled()) Wei.LOGGER.warn("[WEI diag#" + issue + "] " + message, args);
    }

    /** 同じ key では1回だけ出す（毎フレーム呼ばれる場所用） */
    public static void once(int issue, String key, String message, Object... args) {
        if (enabled() && ONCE.add(issue + ":" + key)) log(issue, message, args);
    }

    public static String thread() {
        return Thread.currentThread().getName();
    }

    public static double ms(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 10_000.0) / 100.0;
    }
}
