package io.github.wkwk.wei.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github.wkwk.wei.data.WynnCatalog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * アスペクトのティア：ティアごとに「何個集めればそのティアになるか」と効果を縦に並べる。
 * スロットの数字が必要数。
 */
final class WynnAspectRecipe implements EmiRecipe {
    private static final int WIDTH = 160;
    private static final int TEXT_X = 22;
    private static final int LINE = 10;

    private record Row(EmiStack stack, Component title, List<FormattedCharSequence> effect, int y, int height) {}

    private final EmiRecipeCategory category;
    private final Identifier id;
    private final EmiStack aspect;
    private final List<Row> rows = new ArrayList<>();
    private final int height;

    WynnAspectRecipe(EmiRecipeCategory category, Identifier id, EmiStack aspect, List<WynnCatalog.AspectTier> tiers) {
        this.category = category;
        this.id = id;
        this.aspect = aspect;
        var font = Minecraft.getInstance().font;
        int y = 0;
        for (WynnCatalog.AspectTier tier : tiers) {
            EmiStack stack = aspect.copy().setAmount(Math.max(1, tier.threshold()));
            Component title = Component.translatable("wei.aspect.tier", tier.tier(), tier.threshold())
                    .withStyle(ChatFormatting.WHITE);
            List<FormattedCharSequence> effect = new ArrayList<>();
            for (Component line : tier.effect()) {
                if (line.getString().isBlank()) continue;
                effect.addAll(font.split(line, WIDTH - TEXT_X));
            }
            int h = Math.max(18, LINE + effect.size() * LINE) + 4;
            rows.add(new Row(stack, title, effect, y, h));
            y += h;
        }
        this.height = Math.max(18, y);
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of(aspect);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(aspect);
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return height;
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (Row row : rows) {
            widgets.addSlot(row.stack(), 0, row.y()).recipeContext(this);
            widgets.addText(row.title(), TEXT_X, row.y(), 0xFFFFFF, true);
            int ly = row.y() + LINE;
            for (FormattedCharSequence line : row.effect()) {
                widgets.addText(line, TEXT_X, ly, 0xAAAAAA, false);
                ly += LINE;
            }
        }
    }
}
