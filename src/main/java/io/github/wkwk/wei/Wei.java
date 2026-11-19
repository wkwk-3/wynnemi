package io.github.wkwk.wei;

import io.github.wkwk.wei.emihook.EmiHooks;
import io.github.wkwk.wei.data.WynnDataWatcher;
import io.github.wkwk.wei.data.WynnFavoritesSync;
import io.github.wkwk.wei.data.WynntilsDownloadListener;
import io.github.wkwk.wei.data.WynnCatalog;
import io.github.wkwk.wei.emi.WeiSearchScreen;
import io.github.wkwk.wei.emi.WynnSearchExtension;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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

    private static volatile boolean openSearchNextTick;

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> priceLiteral(String type) {
        return ClientCommandManager.literal("price").then(ClientCommandManager.literal(type)
                .executes(ctx -> setLoot(ctx.getSource(), c -> c.lootHighlightPrice = type)));
    }

    private static int setLoot(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
            java.util.function.Consumer<WeiConfig> change) {
        change.accept(WeiConfig.get());
        WeiConfig.save();
        return lootStatus(source);
    }

    private static int addRule(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
            String type, String rarity, double le) {
        if (!"any".equalsIgnoreCase(type) && io.github.wkwk.wei.loot.LootCategory.byId(type) == null) {
            source.sendError(Component.translatable("wei.loot.unknownType", type));
            return 0;
        }
        WeiConfig.get().lootRules.add(new WeiConfig.LootRule(type, rarity.toLowerCase(java.util.Locale.ROOT), le));
        WeiConfig.save();
        return listRules(source);
    }

    private static int listRules(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        var rules = WeiConfig.get().lootRules;
        if (rules.isEmpty()) source.sendFeedback(Component.translatable("wei.loot.rules.none"));
        for (int i = 0; i < rules.size(); i++) {
            var r = rules.get(i);
            source.sendFeedback(Component.literal((i + 1) + ". " + r.type + " / " + r.rarity + " : "
                    + (r.thresholdLe < 0 ? "off" : r.thresholdLe + " LE")));
        }
        return 1;
    }

    private static int lootStatus(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        WeiConfig c = WeiConfig.get();
        source.sendFeedback(Component.translatable("wei.loot.status",
                Component.translatable(c.lootHighlightEnabled ? "options.on" : "options.off"),
                Component.translatable(c.lootHighlightSound ? "options.on" : "options.off"),
                c.lootHighlightThresholdLe, c.lootHighlightPrice,
                Component.translatable(io.github.wkwk.wei.loot.WynnventoryPrices.isAvailable() ? "wei.loot.wynnventory.ok" : "wei.loot.wynnventory.missing")));
        if (!c.lootRules.isEmpty()) listRules(source);
        return 1;
    }

    @Override
    public void onInitializeClient() {
        WeiConfig.load();
        StartupDiag.logEnvironment();

        // EMI の検索欄で Wynntils のフィルター（level:100-105 rarity:mythic sort:... など）を使えるようにする（フォークの API）
        EmiHooks.registerSearchExtension(new WynnSearchExtension());
        // 一覧ができるまで EMI の読み込みを待ってもらう（空の一覧で読み込むと、お気に入りや履歴が外れる）
        EmiHooks.registerLoadGate("WEI item list", WynnDataWatcher::isCatalogReadyForEmi);

        // Wynntils のデータは起動後に非同期でダウンロードされる。ダウンロード完了イベント（主）と
        // 件数の監視（保険）で、揃ったら・変わったらカタログを作り直して EMI を再読み込みする
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WynntilsDownloadListener.registerOnce();
            io.github.wkwk.wei.loot.LootHighlight.registerOnce();
            WynnDataWatcher.tick();
            WynnFavoritesSync.tick();
            // /wei search はチャットが閉じたあとに開く
            if (openSearchNextTick) {
                openSearchNextTick = false;
                client.setScreen(new WeiSearchScreen(client.screen));
            }
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
                        // 高額アイテムの通知: /wei loot on|off, sound on|off, threshold <LE>, price <種類>
                        .then(ClientCommandManager.literal("loot")
                                .executes(ctx -> lootStatus(ctx.getSource()))
                                .then(ClientCommandManager.literal("on").executes(ctx -> setLoot(ctx.getSource(), c -> c.lootHighlightEnabled = true)))
                                .then(ClientCommandManager.literal("off").executes(ctx -> setLoot(ctx.getSource(), c -> c.lootHighlightEnabled = false)))
                                .then(ClientCommandManager.literal("sound")
                                        .then(ClientCommandManager.literal("on").executes(ctx -> setLoot(ctx.getSource(), c -> c.lootHighlightSound = true)))
                                        .then(ClientCommandManager.literal("off").executes(ctx -> setLoot(ctx.getSource(), c -> c.lootHighlightSound = false))))
                                // /wei loot rule list | add <種類> <レア度> <LE> | add <種類> <レア度> off | remove <番号> | clear
                                .then(ClientCommandManager.literal("rule")
                                        .then(ClientCommandManager.literal("list").executes(ctx -> listRules(ctx.getSource())))
                                        .then(ClientCommandManager.literal("clear").executes(ctx -> {
                                            WeiConfig.get().lootRules.clear();
                                            WeiConfig.save();
                                            return listRules(ctx.getSource());
                                        }))
                                        .then(ClientCommandManager.literal("remove")
                                                .then(ClientCommandManager.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(ctx -> {
                                                    int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n");
                                                    var rules = WeiConfig.get().lootRules;
                                                    if (n <= rules.size()) rules.remove(n - 1);
                                                    WeiConfig.save();
                                                    return listRules(ctx.getSource());
                                                })))
                                        .then(ClientCommandManager.literal("add")
                                                .then(ClientCommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .suggests((ctx, b) -> {
                                                            b.suggest("any");
                                                            for (var c : io.github.wkwk.wei.loot.LootCategory.values()) b.suggest(c.id);
                                                            return b.buildFuture();
                                                        })
                                                        .then(ClientCommandManager.argument("rarity", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                                .suggests((ctx, b) -> {
                                                                    var c = io.github.wkwk.wei.loot.LootCategory.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "type"));
                                                                    String[] list = c == null ? new String[] {"any"} : c.rarities();
                                                                    for (String r : list) b.suggest(r);
                                                                    return b.buildFuture();
                                                                })
                                                                .then(ClientCommandManager.literal("off").executes(ctx -> addRule(ctx.getSource(),
                                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "type"), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "rarity"), -1)))
                                                                .then(ClientCommandManager.argument("le", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0)).executes(ctx -> addRule(ctx.getSource(),
                                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "type"), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "rarity"),
                                                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "le"))))))))
                                .then(ClientCommandManager.literal("threshold")
                                        .then(ClientCommandManager.argument("le", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> setLoot(ctx.getSource(), c -> c.lootHighlightThresholdLe =
                                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "le")))))
                                .then(priceLiteral("lowest")).then(priceLiteral("average")).then(priceLiteral("average80"))
                                .then(priceLiteral("median")).then(priceLiteral("highest")))
                        // 条件設定の画面を開く
                        .then(ClientCommandManager.literal("search").executes(ctx -> {
                            openSearchNextTick = true;
                            return 1;
                        }))
                        // Wynntils のデータからアイテム一覧を作り直す
                        .then(ClientCommandManager.literal("rebuild").executes(ctx -> {
                            WynnDataWatcher.requestRebuild();
                            ctx.getSource().sendFeedback(Component.translatable("wei.command.rebuild"));
                            return 1;
                        }))));

        // EMI の検索欄の右に、Wynncraft 用の条件検索を開く虫眼鏡ボタンを置く
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>) || !WynnCatalog.isOnWynncraftNow()) return;
            Button button = Button.builder(Component.empty(), b -> client.setScreen(new WeiSearchScreen(screen)))
                    .bounds(-100, -100, 18, 18)
                    .tooltip(Tooltip.create(Component.translatable("wei.search.title")))
                    .build();
            Screens.getButtons(screen).add(button);
            // EMI は検索欄の位置を描画のたびに決めるので、こちらも毎回合わせる
            ScreenEvents.beforeRender(screen).register((s, g, mx, my, delta) -> {
                var search = dev.emi.emi.screen.EmiScreenManager.search;
                button.visible = search != null && search.visible && search.getWidth() > 0;
                if (button.visible) {
                    button.setX(search.getX() + search.getWidth() + 2);
                    button.setY(search.getY());
                }
            });
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                if (button.visible) io.github.wkwk.wei.emi.SearchIcon.draw(g, button.getX() + 3, button.getY() + 3);
            });
        });
    }
}
