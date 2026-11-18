package io.github.wkwk.wei;

import dev.emi.emi.fork.EmiForkApi;
import io.github.wkwk.wei.data.WynnDataWatcher;
import io.github.wkwk.wei.data.WynnFavoritesSync;
import io.github.wkwk.wei.data.WynntilsDownloadListener;
import io.github.wkwk.wei.emi.WynnSearchExtension;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Wei implements ClientModInitializer {
    public static final String MOD_ID = "wei";
    public static final Logger LOGGER = LoggerFactory.getLogger("WEI");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        WeiConfig.load();
        StartupDiag.logEnvironment();

        // EMI の検索欄で Wynntils のフィルター（level:100-105 rarity:mythic sort:... など）を使えるようにする（フォークの API）
        EmiForkApi.registerSearchExtension(new WynnSearchExtension());
        // 一覧ができるまで EMI の読み込みを待ってもらう（空の一覧で読み込むと、お気に入りや履歴が外れる）
        EmiForkApi.registerLoadGate("WEI item list", WynnDataWatcher::isCatalogReadyForEmi);

        // Wynntils のデータは起動後に非同期でダウンロードされる。ダウンロード完了イベント（主）と
        // 件数の監視（保険）で、揃ったら・変わったらカタログを作り直して EMI を再読み込みする
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WynntilsDownloadListener.registerOnce();
            WynnDataWatcher.tick();
            WynnFavoritesSync.tick();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommandManager.literal("wei")
                        // 設定を読み直して EMI だけ再読み込み（アイテム一覧は作り直さない）
                        .then(ClientCommandManager.literal("reload").executes(ctx -> {
                            WeiConfig.load();
                            WynnDataWatcher.requestEmiReload();
                            ctx.getSource().sendFeedback(Component.translatable("wei.command.reload"));
                            return 1;
                        }))
                        // Wynntils のデータからアイテム一覧を作り直す
                        .then(ClientCommandManager.literal("rebuild").executes(ctx -> {
                            WynnDataWatcher.requestRebuild();
                            ctx.getSource().sendFeedback(Component.translatable("wei.command.rebuild"));
                            return 1;
                        }))
                        // 一覧のアイテムをすべて config/wei-export.json に書き出す
                        .then(ClientCommandManager.literal("export").executes(ctx -> {
                            try {
                                java.nio.file.Path out = io.github.wkwk.wei.data.WynnExport.export();
                                ctx.getSource().sendFeedback(Component.literal("WEI: " + io.github.wkwk.wei.data.WynnCatalog.current().entries().size() + " items -> " + out));
                            } catch (Exception e) {
                                LOGGER.error("WEI export failed", e);
                                ctx.getSource().sendError(Component.literal("WEI export failed: " + e));
                            }
                            return 1;
                        }))));
    }
}
