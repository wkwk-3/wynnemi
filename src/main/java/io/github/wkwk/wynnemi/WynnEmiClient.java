package io.github.wkwk.wynnemi;

import io.github.wkwk.wynnemi.data.WynnDataWatcher;
import io.github.wkwk.wynnemi.data.WynnFavoritesSync;
import io.github.wkwk.wynnemi.data.WynntilsDownloadListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WynnEmiClient implements ClientModInitializer {
    public static final String MOD_ID = "wynnemi";
    public static final Logger LOGGER = LoggerFactory.getLogger("WynnEMI");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        WynnEmiConfig.load();

        // Wynntils のデータは起動後に非同期でダウンロードされる。ダウンロード完了イベント（主）と
        // 件数の監視（保険）で、揃ったら・変わったらカタログを作り直して EMI を再読み込みする
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WynntilsDownloadListener.registerOnce();
            WynnDataWatcher.tick();
            WynnFavoritesSync.tick();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommandManager.literal("wynnemi")
                        // 設定を読み直して EMI だけ再読み込み（アイテム一覧は作り直さない）
                        .then(ClientCommandManager.literal("reload").executes(ctx -> {
                            WynnEmiConfig.load();
                            WynnDataWatcher.requestEmiReload();
                            ctx.getSource().sendFeedback(Component.translatable("wynnemi.command.reload"));
                            return 1;
                        }))
                        // Wynntils のデータからアイテム一覧を作り直す
                        .then(ClientCommandManager.literal("rebuild").executes(ctx -> {
                            WynnDataWatcher.requestRebuild();
                            ctx.getSource().sendFeedback(Component.translatable("wynnemi.command.rebuild"));
                            return 1;
                        }))));
    }
}
