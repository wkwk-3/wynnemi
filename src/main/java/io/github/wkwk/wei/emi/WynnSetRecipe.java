package io.github.wkwk.wei.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * セット装備：左にセット（説明文にボーナス）、右にメンバーを並べる。
 * メンバーで R → 所属するセット、セットで U → このレシピ。
 */
final class WynnSetRecipe implements EmiRecipe {
    private static final int WIDTH = 134;
    private static final int PER_ROW = 4;
    private static final int MEMBERS_X = 52;

    private final EmiRecipeCategory category;
    private final Identifier id;
    private final EmiStack set;
    private final List<EmiStack> members;

    WynnSetRecipe(EmiRecipeCategory category, Identifier id, EmiStack set, List<EmiStack> members) {
        this.category = category;
        this.id = id;
        this.set = set;
        this.members = List.copyOf(members);
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
        return List.of(set);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return members;
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        int rows = Math.max(1, (members.size() + PER_ROW - 1) / PER_ROW);
        return rows * 18;
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int rows = Math.max(1, (members.size() + PER_ROW - 1) / PER_ROW);
        int centerY = (rows * 18 - 18) / 2;
        widgets.addSlot(set, 0, centerY);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 22, centerY + 1);
        for (int i = 0; i < members.size(); i++) {
            int x = MEMBERS_X + (i % PER_ROW) * 18;
            int y = (i / PER_ROW) * 18;
            widgets.addSlot(members.get(i), x, y).recipeContext(this);
        }
    }
}
