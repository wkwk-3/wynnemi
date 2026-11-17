package io.github.wkwk.wynnemi.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 「A → B」を表示するだけのレシピ。入手方法（入手元 → アイテム）、
 * 製作の使い道（素材 → 職業）、採集（職業 → 素材）に使う。
 */
final class WynnLinkRecipe implements EmiRecipe {
    private static final int WIDTH = 134;
    private static final int TEXT_COLOR = 0xFF404040;

    private final EmiRecipeCategory category;
    private final Identifier id;
    private final EmiStack input;
    private final EmiStack output;
    private final @Nullable Component caption;

    WynnLinkRecipe(EmiRecipeCategory category, Identifier id, EmiStack input, EmiStack output, @Nullable Component caption) {
        this.category = category;
        this.id = id;
        this.input = input;
        this.output = output;
        this.caption = caption;
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
        return List.of(input);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(output);
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return caption == null ? 18 : 30;
    }

    /** 入力が仮想アイテムなので、レシピツリー（必要素材の計算）には出さない */
    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(input, 24, 0);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 55, 1);
        widgets.addSlot(output, 92, 0).recipeContext(this);

        if (caption != null) {
            Font font = Minecraft.getInstance().font;
            String text = caption.getString();
            if (font.width(text) > WIDTH) {
                text = font.plainSubstrByWidth(text, WIDTH - font.width("...")) + "...";
            }
            widgets.addText(Component.literal(text), WIDTH / 2, 21, TEXT_COLOR, false)
                    .horizontalAlign(TextWidget.Alignment.CENTER);
        }
    }
}
