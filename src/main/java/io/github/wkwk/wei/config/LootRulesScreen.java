package io.github.wkwk.wei.config;

import io.github.wkwk.wei.WeiConfig;
import io.github.wkwk.wei.loot.LootCategory;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * 高額通知の種類・レア度別の基準の編集画面。
 * 上が追加行、下が登録済みの一覧。
 */
final class LootRulesScreen extends Screen {
    private static final int ROW = 22;

    private final Screen parent;
    private final WeiConfig config = WeiConfig.get();

    private String type = "any";
    private String rarity = "any";
    private String amount = "";
    private int page;

    LootRulesScreen(Screen parent) {
        super(Component.translatable("wei.rules.title"));
        this.parent = parent;
    }

    private static String[] types() {
        LootCategory[] all = LootCategory.values();
        String[] out = new String[all.length + 1];
        out[0] = "any";
        for (int i = 0; i < all.length; i++) out[i + 1] = all[i].id;
        return out;
    }

    private String[] rarities() {
        LootCategory c = LootCategory.byId(type);
        return c == null ? new String[] {"any", "normal", "unique", "rare", "set", "legendary", "fabled", "mythic"}
                : c.rarities();
    }

    @Override
    protected void init() {
        int left = width / 2 - 160;
        int y = 34;

        addRenderableWidget(Button.builder(typeLabel(type), b -> {
                    type = next(types(), type);
                    rarity = "any";
                    rebuildWidgets();
                })
                .bounds(left, y, 100, 20)
                .build());
        addRenderableWidget(Button.builder(rarityLabel(rarity), b -> {
                    rarity = next(rarities(), rarity);
                    b.setMessage(rarityLabel(rarity));
                })
                .bounds(left + 104, y, 90, 20)
                .build());
        EditBox amountBox = new EditBox(font, left + 198, y + 1, 56, 18, Component.translatable("wei.rules.amount"));
        amountBox.setFilter(v -> v.matches("[0-9]*\\.?[0-9]*"));
        amountBox.setHint(Component.translatable("wei.rules.amount.hint"));
        amountBox.setValue(amount);
        amountBox.setResponder(v -> amount = v);
        addRenderableWidget(amountBox);
        addRenderableWidget(Button.builder(Component.translatable("wei.rules.add"), b -> add(false))
                .bounds(left + 258, y, 30, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("wei.rules.off"), b -> add(true))
                .bounds(left + 290, y, 30, 20)
                .build());

        int listTop = y + 34;
        int perPage = Math.max(1, (height - listTop - 40) / ROW);
        List<WeiConfig.LootRule> rules = config.lootRules;
        int pages = Math.max(1, (rules.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        for (int i = page * perPage; i < Math.min(rules.size(), (page + 1) * perPage); i++) {
            int index = i;
            int rowY = listTop + (i - page * perPage) * ROW;
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                        config.lootRules.remove(index);
                        WeiConfig.save();
                        rebuildWidgets();
                    })
                    .bounds(left + 300, rowY, 20, 20)
                    .build());
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                        page = (page + pages - 1) % pages;
                        rebuildWidgets();
                    })
                    .bounds(left, height - 28, 20, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                        page = (page + 1) % pages;
                        rebuildWidgets();
                    })
                    .bounds(left + 24, height - 28, 20, 20)
                    .build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 60, height - 28, 120, 20)
                .build());
    }

    /** off なら「通知しない」で登録 */
    private void add(boolean off) {
        double le;
        if (off) {
            le = -1;
        } else {
            try {
                le = Double.parseDouble(amount);
            } catch (NumberFormatException e) {
                return;
            }
        }
        // 同じ組み合わせは置き換え
        config.lootRules.removeIf(r -> r.type.equalsIgnoreCase(type) && r.rarity.equalsIgnoreCase(rarity));
        config.lootRules.add(new WeiConfig.LootRule(type, rarity, le));
        WeiConfig.save();
        amount = "";
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = width / 2 - 160;
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("wei.rules.help", trim(config.lootHighlightThresholdLe)),
                left, 22, 0xFFAAAAAA);
        int listTop = 34 + 34;
        g.drawString(font, Component.translatable("wei.rules.list"), left, listTop - 11, 0xFFFFD27F);
        int perPage = Math.max(1, (height - listTop - 40) / ROW);
        List<WeiConfig.LootRule> rules = config.lootRules;
        if (rules.isEmpty()) {
            g.drawString(font, Component.translatable("wei.rules.empty"), left, listTop + 6, 0xFFAAAAAA);
        }
        for (int i = page * perPage; i < Math.min(rules.size(), (page + 1) * perPage); i++) {
            WeiConfig.LootRule r = rules.get(i);
            int rowY = listTop + (i - page * perPage) * ROW;
            Component text = typeLabel(r.type).copy()
                    .append(" / ")
                    .append(rarityLabel(r.rarity))
                    .append(" : ")
                    .append(r.thresholdLe < 0 ? Component.translatable("wei.rules.never")
                            : Component.literal(trim(r.thresholdLe) + " LE"));
            g.drawString(font, text, left, rowY + 6, 0xFFFFFFFF);
        }
    }

    private static Component typeLabel(String id) {
        return Component.translatable("wei.loot.type." + id);
    }

    private static Component rarityLabel(String id) {
        return Component.translatable("wei.loot.rarity." + id);
    }

    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    @Override
    public void onClose() {
        WeiConfig.save();
        minecraft.setScreen(parent);
    }
}
