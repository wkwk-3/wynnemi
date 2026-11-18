package io.github.wkwk.wei.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github.wkwk.wei.data.WynnCatalog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * アスペクトのティア：1行に1ティア。左にそのティアのアスペクト（カーソルを合わせると、そのティアの効果）、
 * 右に「ティア 2：累計 5 個」。
 */
final class WynnAspectRecipe implements EmiRecipe {
    private static final int WIDTH = 140;
    private static final int ROW = 20;
    /** 1.21.6 以降は文字色に不透明度（先頭の FF）が要る。無いと透明で何も見えない */
    private static final int TEXT_COLOR = 0xFF404040;

    private final EmiRecipeCategory category;
    private final Identifier id;
    private final EmiStack aspect;
    private final List<EmiStack> tierStacks = new ArrayList<>();
    private final List<Component> titles = new ArrayList<>();

    WynnAspectRecipe(EmiRecipeCategory category, Identifier id, WynnEmiStack aspect, List<WynnCatalog.AspectTier> tiers) {
        this.category = category;
        this.id = id;
        this.aspect = aspect;
        for (WynnCatalog.AspectTier tier : tiers) {
            // キーは一覧のアスペクトと同じ（お気に入りなどは1件のアスペクトとして扱われる）。見た目と説明文だけそのティア
            List<Component> tooltip = tier.tooltip().size() > 1 ? tier.tooltip() : List.of(aspect.getName());
            tierStacks.add(WynnEmiStack.ofItem(aspect.key(), tier.stack(), aspect.getName(), tooltip));
            titles.add(Component.translatable("wei.aspect.tier", tier.tier(), tier.threshold()));
        }
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
        return Math.max(18, tierStacks.size() * ROW - 2);
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < tierStacks.size(); i++) {
            int y = i * ROW;
            widgets.addSlot(tierStacks.get(i), 0, y).recipeContext(this);
            widgets.addText(titles.get(i), 22, y + 5, TEXT_COLOR, false);
        }
    }
}
