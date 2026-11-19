package io.github.wkwk.wei;

import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 調査用ログ。行頭に {@code [WEI diag#番号]} を付ける（番号は docs/DIAGNOSTICS.md）。
 *
 * <p>既定: プレリリース（バージョンに「-」を含む）は出す、リリースは出さない。
 * config/wei.json の {@code diagnosticsOverride}（true / false）か、JVM 引数 {@code -Dwei.diag=true|false} で上書きできる。
 * 引数の組み立てが重い呼び出しは {@link #enabled()} で囲む。
 */
public final class Diag {
    private static final String SYSTEM = System.getProperty("wei.diag");
    private static final boolean PRERELEASE = FabricLoader.getInstance().getModContainer("wei")
            .map(m -> LogPolicy.isPrerelease(m.getMetadata().getVersion().getFriendlyString()))
            .orElse(false);
    private static final Set<String> ONCE = ConcurrentHashMap.newKeySet();
    private static final Set<String> SWALLOWED = ConcurrentHashMap.newKeySet();

    private Diag() {}

    public static boolean enabled() {
        return LogPolicy.enabled(SYSTEM, WeiConfig.get().diagnosticsOverride, PRERELEASE);
    }

    public static boolean prerelease() {
        return PRERELEASE;
    }

    public static void log(int issue, String message, Object... args) {
        if (enabled()) Wei.LOGGER.info("[WEI diag#" + issue + "] " + message, args);
    }

    public static void warn(int issue, String message, Object... args) {
        if (enabled()) Wei.LOGGER.warn("[WEI diag#" + issue + "] " + message, args);
    }

    /** 同じ key は 1 回だけ（毎フレーム呼ばれる箇所用） */
    public static void once(int issue, String key, String message, Object... args) {
        if (enabled() && ONCE.add(issue + ":" + key)) log(issue, message, args);
    }

    /**
     * 握りつぶした例外を、場所（where）ごとに 1 回だけ記録する。
     *
     * <p>落とさずに続ける方針の箇所で、何も残らずに機能だけ効かなくなるのを避けるため、
     * 調査用ログの設定にかかわらず警告を 1 行出す。スタックトレースは調査用ログが有効なときだけ。
     */
    public static void swallowed(String where, Throwable t) {
        if (!SWALLOWED.add(where)) return;
        if (enabled()) {
            Wei.LOGGER.warn("[WEI] Ignored an error in {} (shown once)", where, t);
        } else {
            Wei.LOGGER.warn("[WEI] Ignored an error in {} (shown once): {}", where, t.toString());
        }
    }

    public static String thread() {
        return Thread.currentThread().getName();
    }

    public static double ms(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 10_000.0) / 100.0;
    }
}
