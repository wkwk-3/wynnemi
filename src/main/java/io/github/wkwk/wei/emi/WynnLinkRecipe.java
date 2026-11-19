package io.github.wkwk.wei.emi;

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
 * 「A → B」を表示するだけのレシピ。入手（入手元 → アイテム）、製作（素材 → 職業）、
 * 採集（職業 → 素材）、必要クエスト（クエスト → 装備）、未鑑定ボックス（ボックス → 候補）に使う。
 *
 * <p>EMI は R で出力、U で入力から引く。見た目の向きと引く向きを別に指定できる
 * （例: ボックスの R で中身の候補を出す）。
 */
final class WynnLinkRecipe implements EmiRecipe {
    private static final int WIDTH = 134;
    private static final int TEXT_COLOR = 0xFF404040;

    private final EmiRecipeCategory category;
    private final Identifier id;
    private final EmiStack left;
    private final EmiStack right;
    private final @Nullable Component caption;
    private final boolean lookupReversed;
    private final int sortKey;

    WynnLinkRecipe(EmiRecipeCategory category, Identifier id, EmiStack left, EmiStack right, @Nullable Component caption) {
        this(category, id, left, right, caption, false, 0);
    }

    /**
     * @param lookupReversed true なら、左で R・右で U を引く（見た目は左 → 右のまま）
     * @param sortKey カテゴリ内の並び順（小さい順）
     */
    WynnLinkRecipe(EmiRecipeCategory category, Identifier id, EmiStack left, EmiStack right, @Nullable Component caption,
            boolean lookupReversed, int sortKey) {
        this.category = category;
        this.id = id;
        this.left = left;
        this.right = right;
        this.caption = caption;
        this.lookupReversed = lookupReversed;
        this.sortKey = sortKey;
    }

    int sortKey() {
        return sortKey;
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
        return List.of(lookupReversed ? right : left);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(lookupReversed ? left : right);
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return caption == null ? 18 : 30;
    }

    /** 入力が仮想アイテムのためレシピツリーには出さない */
    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(left, 24, 0);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 55, 1);
        widgets.addSlot(right, 92, 0).recipeContext(this);

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
