package io.github.wkwk.wynncodex.emi;

import io.github.wkwk.wynncodex.WynnCodex;
import java.lang.reflect.Method;

/**
 * EMI の再読み込みは公開 API に無く、内部クラス EmiReloadManager にしかないためリフレクションで呼ぶ。
 * 見つからない場合は警告だけ出す（次にワールドへ入り直したとき EMI が自分で読み込む）。
 */
public final class EmiReloadHelper {
    private static Method reloadMethod;
    private static boolean lookedUp;

    private EmiReloadHelper() {}

    public static void reload() {
        try {
            if (!lookedUp) {
                lookedUp = true;
                Class<?> manager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
                reloadMethod = manager.getMethod("reload");
            }
            if (reloadMethod != null) {
                reloadMethod.invoke(null);
            }
        } catch (Throwable t) {
            WynnCodex.LOGGER.warn("Could not trigger EMI reload; rejoin the world to refresh EMI", t);
        }
    }
}
