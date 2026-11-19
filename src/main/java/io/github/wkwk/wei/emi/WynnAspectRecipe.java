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
    private final WynnEmiStack aspect;
    private final List<WynnCatalog.AspectTier> tiers;
    /** ティアごとの見た目と説明文。レシピ画面を初めて開いたとき（描画スレッド）に作る */
    private List<EmiStack> tierStacks;
    private final List<Component> titles = new ArrayList<>();

    WynnAspectRecipe(EmiRecipeCategory category, Identifier id, WynnEmiStack aspect, List<WynnCatalog.AspectTier> tiers) {
        this.category = category;
        this.id = id;
        this.aspect = aspect;
        this.tiers = tiers;
        for (WynnCatalog.AspectTier tier : tiers) {
            titles.add(Component.translatable("wei.aspect.tier", tier.tier(), tier.threshold()));
        }
    }

    private List<EmiStack> tierStacks() {
        if (tierStacks == null) {
            List<EmiStack> out = new ArrayList<>();
            for (WynnCatalog.AspectTier tier : tiers) {
                // キーは一覧のアスペクトと同じ（お気に入りなどは1件のアスペクトとして扱われる）。見た目と説明文だけそのティア
                var stack = tier.createStack();
                List<Component> tooltip = WynnCatalog.tooltipLines(stack);
                if (tooltip.size() <= 1) tooltip = List.of(aspect.getName());
                out.add(WynnEmiStack.ofItem(aspect.key(), stack, aspect.getName(), tooltip));
            }
            tierStacks = out;
        }
        return tierStacks;
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
        return Math.max(18, tiers.size() * ROW - 2);
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        List<EmiStack> stacks = tierStacks();
        for (int i = 0; i < stacks.size(); i++) {
            int y = i * ROW;
            widgets.addSlot(stacks.get(i), 0, y).recipeContext(this);
            widgets.addText(titles.get(i), 22, y + 5, TEXT_COLOR, false);
        }
    }
}
