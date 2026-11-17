package io.github.wkwk.wei.emi;

import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.Wei;
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
                Diag.log(11, "EMI reload invoked via reflection");
            } else {
                Diag.warn(11, "EMI reload method not found; reload skipped");
            }
        } catch (Throwable t) {
            Wei.LOGGER.warn("Could not trigger EMI reload; rejoin the world to refresh EMI", t);
        }
    }
}
