package io.github.wkwk.wei.data;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.net.event.DownloadEvent;
import io.github.wkwk.wei.Wei;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Wynntils はすべてのデータのダウンロードと処理が終わると、メインスレッドで
 * DownloadEvent.Completed（一部失敗なら Failed）を出す。これを受けてカタログを作り直す。
 */
public final class WynntilsDownloadListener {
    private static boolean registered;

    /**
     * Wynntils のイベントバスは Wynntils の初期化中に作られ、MOD の初期化順は決まっていないため、
     * 最初のクライアントティックで登録する。失敗しても件数の監視で動き続ける。
     */
    public static void registerOnce() {
        if (registered) return;
        registered = true;
        try {
            WynntilsMod.registerEventListener(new WynntilsDownloadListener());
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
        // 一部失敗でも、読めた分は作り直す（失敗した種類の再試行は WynnDataWatcher が行う）
        WynnDataWatcher.onWynntilsDataDownloaded();
    }
}
