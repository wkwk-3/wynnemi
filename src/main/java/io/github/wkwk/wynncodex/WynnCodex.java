package io.github.wkwk.wynncodex;

import dev.emi.emi.fork.EmiForkApi;
import io.github.wkwk.wynncodex.data.WynnDataWatcher;
import io.github.wkwk.wynncodex.data.WynnFavoritesSync;
import io.github.wkwk.wynncodex.data.WynntilsDownloadListener;
import io.github.wkwk.wynncodex.emi.WynnSearchExtension;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WynnCodex implements ClientModInitializer {
    public static final String MOD_ID = "wynncodex";
    public static final Logger LOGGER = LoggerFactory.getLogger("WynnCodex");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        WynnCodexConfig.load();

        // EMI の検索欄で Wynntils のフィルター（level:100-105 rarity:mythic sort:... など）を使えるようにする（フォークの API）
        EmiForkApi.registerSearchExtension(new WynnSearchExtension());

        // Wynntils のデータは起動後に非同期でダウンロードされる。ダウンロード完了イベント（主）と
        // 件数の監視（保険）で、揃ったら・変わったらカタログを作り直して EMI を再読み込みする
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WynntilsDownloadListener.registerOnce();
            WynnDataWatcher.tick();
            WynnFavoritesSync.tick();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommandManager.literal("wynncodex")
                        // 設定を読み直して EMI だけ再読み込み（アイテム一覧は作り直さない）
                        .then(ClientCommandManager.literal("reload").executes(ctx -> {
                            WynnCodexConfig.load();
                            WynnDataWatcher.requestEmiReload();
                            ctx.getSource().sendFeedback(Component.translatable("wynncodex.command.reload"));
                            return 1;
                        }))
                        // Wynntils のデータからアイテム一覧を作り直す
                        .then(ClientCommandManager.literal("rebuild").executes(ctx -> {
                            WynnDataWatcher.requestRebuild();
                            ctx.getSource().sendFeedback(Component.translatable("wynncodex.command.rebuild"));
                            return 1;
                        }))));
    }
}
