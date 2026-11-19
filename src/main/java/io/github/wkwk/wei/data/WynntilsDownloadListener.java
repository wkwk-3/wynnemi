package io.github.wkwk.wei.data;

import io.github.wkwk.wei.Diag;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.net.event.DownloadEvent;
import io.github.wkwk.wei.Wei;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Wynntils の全データの取得完了（DownloadEvent.Completed / Failed）でカタログを作り直す。
 */
public final class WynntilsDownloadListener {
    private static boolean registered;

    /**
     * Wynntils のイベントバスは Wynntils の初期化中に作られ、MOD の初期化順は不定のため最初のティックで登録する。
     * 失敗しても件数の監視で動く。
     */
    public static void registerOnce() {
        if (registered) return;
        registered = true;
        try {
            WynntilsMod.registerEventListener(new WynntilsDownloadListener());
            Diag.log(3, "Listening to Wynntils download events");
        } catch (Throwable t) {
            Wei.LOGGER.warn("Could not listen to Wynntils download events; falling back to polling", t);
        }
    }

    @SubscribeEvent
    public void onCompleted(DownloadEvent.Completed event) {
        WynnDataWatcher.onWynntilsDataDownloaded();
    }

    @SubscribeEvent
    public void onFailed(DownloadEvent.Failed event) {
        // 一部失敗でも読めた分で作る（再試行は WynnDataWatcher）
        WynnDataWatcher.onWynntilsDataDownloaded();
    }
}
