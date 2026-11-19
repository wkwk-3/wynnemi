package io.github.wkwk.wei.config;

import io.github.wkwk.wei.WeiConfig;
import io.github.wkwk.wei.data.WynnDataWatcher;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 設定画面。閉じると保存し、一覧・レシピに関わる設定が変わっていれば EMI を読み込み直す。 */
public final class WeiConfigScreen extends Screen {
    private static final String[] PRICE_TYPES = {"lowest", "average", "average80", "median", "highest"};

    private final @Nullable Screen parent;
    private final WeiConfig config = WeiConfig.get();
    /** EMI の再読み込みが要る設定の、開いた時点の値 */
    private final String reloadSnapshot;

    private EditBox thresholdBox;
    private EditBox colorBox;

    public WeiConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("wei.config.title"));
        this.parent = parent;
        this.reloadSnapshot = reloadState();
    }

    private String reloadState() {
        return config.hideVanillaOnWynncraft + "," + config.showSourcesInIndex + "," + config.showObtainRecipes + ","
                + config.showProfessionRecipes + "," + config.showGearRecipes;
    }

    @Override
    protected void init() {
        int colW = 150;
        int left = width / 2 - colW - 5;
        int right = width / 2 + 5;
        int y = 40;

        // 左: アイテム一覧
        int ly = y;
        ly = toggle(left, ly, colW, "hideVanilla", () -> config.hideVanillaOnWynncraft, v -> config.hideVanillaOnWynncraft = v);
        ly = toggle(left, ly, colW, "showSources", () -> config.showSourcesInIndex, v -> config.showSourcesInIndex = v);
        ly = toggle(left, ly, colW, "obtainRecipes", () -> config.showObtainRecipes, v -> config.showObtainRecipes = v);
        ly = toggle(left, ly, colW, "professionRecipes", () -> config.showProfessionRecipes, v -> config.showProfessionRecipes = v);
        ly = toggle(left, ly, colW, "gearRecipes", () -> config.showGearRecipes, v -> config.showGearRecipes = v);
        ly = toggle(left, ly, colW, "syncFavorites", () -> config.syncFavoritesToWynntils, v -> config.syncFavoritesToWynntils = v);
        // 自動（プレリリースのみ出す）→ オン → オフ → 自動
        addRenderableWidget(Button.builder(diagnosticsLabel(), b -> {
                    Boolean v = config.diagnosticsOverride;
                    config.diagnosticsOverride = v == null ? Boolean.TRUE : v ? Boolean.FALSE : null;
                    b.setMessage(diagnosticsLabel());
                })
                .bounds(left, ly, colW, 20)
                .tooltip(Tooltip.create(Component.translatable("wei.config.diagnostics.tooltip")))
                .build());
        ly += 24;

        // 右: 高額アイテムの通知
        int ry = y;
        ry = toggle(right, ry, colW, "lootEnabled", () -> config.lootHighlightEnabled, v -> config.lootHighlightEnabled = v);
        ry = toggle(right, ry, colW, "lootSound", () -> config.lootHighlightSound, v -> config.lootHighlightSound = v);
        addRenderableWidget(Button.builder(priceLabel(), b -> {
                    config.lootHighlightPrice = next(config.lootHighlightPrice);
                    b.setMessage(priceLabel());
                })
                .bounds(right, ry, colW, 20)
                .tooltip(Tooltip.create(Component.translatable("wei.config.lootPrice.tooltip")))
                .build());
        ry += 24;
        thresholdBox = new EditBox(font, right + 90, ry, colW - 90, 18, Component.translatable("wei.config.lootThreshold"));
        thresholdBox.setValue(trim(config.lootHighlightThresholdLe));
        thresholdBox.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        addRenderableWidget(thresholdBox);
        ry += 24;
        colorBox = new EditBox(font, right + 90, ry, colW - 90, 18, Component.translatable("wei.config.lootColor"));
        colorBox.setValue(String.format("%08X", config.lootHighlightColor));
        colorBox.setFilter(s -> s.matches("[0-9a-fA-F]{0,8}"));
        addRenderableWidget(colorBox);
        ry += 24;

        addRenderableWidget(Button.builder(Component.translatable("wei.config.lootRules"), b -> {
                    applyBoxes();
                    minecraft.setScreen(new LootRulesScreen(this));
                })
                .bounds(right, ry, colW, 20)
                .tooltip(Tooltip.create(Component.translatable("wei.config.lootRules.tooltip")))
                .build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20)
                .build());
    }

    private int toggle(int x, int y, int w, String id, Supplier<Boolean> get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(toggleLabel(id, get.get()), b -> {
                    set.accept(!get.get());
                    b.setMessage(toggleLabel(id, get.get()));
                })
                .bounds(x, y, w, 20)
                .tooltip(Tooltip.create(Component.translatable("wei.config." + id + ".tooltip")))
                .build());
        return y + 24;
    }

    private static Component toggleLabel(String id, boolean on) {
        return Component.translatable("wei.config." + id)
                .append(": ")
                .append(on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private Component diagnosticsLabel() {
        Boolean v = config.diagnosticsOverride;
        Component state = v != null ? (v ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF)
                : Component.translatable("wei.config.diagnostics.auto",
                        io.github.wkwk.wei.Diag.prerelease() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
        return Component.translatable("wei.config.diagnostics").append(": ").append(state);
    }

    private Component priceLabel() {
        return Component.translatable("wei.config.lootPrice")
                .append(": ")
                .append(Component.translatable("wei.config.price." + config.lootHighlightPrice.toLowerCase(Locale.ROOT)));
    }

    private static String next(String current) {
        for (int i = 0; i < PRICE_TYPES.length; i++) {
            if (PRICE_TYPES[i].equalsIgnoreCase(current)) return PRICE_TYPES[(i + 1) % PRICE_TYPES.length];
        }
        return PRICE_TYPES[0];
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        int left = width / 2 - 155;
        int right = width / 2 + 5;
        g.drawString(font, Component.translatable("wei.config.section.list"), left, 28, 0xFFFFD27F);
        g.drawString(font, Component.translatable("wei.config.section.loot"), right, 28, 0xFFFFD27F);
        if (thresholdBox != null) {
            g.drawString(font, Component.translatable("wei.config.lootThreshold"), right, thresholdBox.getY() + 5, 0xFFE0E0E0);
            g.drawString(font, Component.translatable("wei.config.lootColor"), right, colorBox.getY() + 5, 0xFFE0E0E0);
            int c = parseColor(colorBox.getValue(), config.lootHighlightColor);
            g.fill(right + 70, colorBox.getY() + 1, right + 86, colorBox.getY() + 17, c);
        }
    }

    private static int parseColor(String hex, int fallback) {
        try {
            if (hex.length() == 6) hex = "FF" + hex;
            return hex.length() == 8 ? (int) Long.parseLong(hex, 16) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 入力欄を設定に反映（保存はしない） */
    private void applyBoxes() {
        try {
            double v = Double.parseDouble(thresholdBox.getValue());
            if (v >= 0) config.lootHighlightThresholdLe = v;
        } catch (RuntimeException ignored) {
            // 数値でなければ据え置き
        }
        config.lootHighlightColor = parseColor(colorBox.getValue(), config.lootHighlightColor);
    }

    @Override
    public void onClose() {
        applyBoxes();
        WeiConfig.save();
        if (!reloadState().equals(reloadSnapshot)) WynnDataWatcher.requestEmiReload();
        minecraft.setScreen(parent);
    }
}
