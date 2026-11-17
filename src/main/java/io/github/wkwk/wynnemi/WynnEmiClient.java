package io.github.wkwk.wynnemi;

import io.github.wkwk.wynnemi.data.WynnDataWatcher;
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

        // Wynntils のデータは起動後に非同期でダウンロードされるので、毎秒監視して
        // 揃ったら（または変化したら）カタログを作り直し、EMI を再読み込みする
        ClientTickEvents.END_CLIENT_TICK.register(client -> WynnDataWatcher.tick());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommandManager.literal("wynnemi")
                        .then(ClientCommandManager.literal("reload").executes(ctx -> {
                            WynnEmiConfig.load();
                            WynnDataWatcher.requestReload();
                            ctx.getSource().sendFeedback(Component.translatable("wynnemi.command.reload"));
                            return 1;
                        }))));
    }
}
