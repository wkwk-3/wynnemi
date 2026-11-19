package io.github.wkwk.wei.emimixin;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import io.github.wkwk.wei.emihook.EmiHooks;
import java.util.List;
import java.util.Map;
import io.github.wkwk.wei.emihook.WeiHooked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** R/U のレシピ画面の差し替え口（手持ちの未鑑定ボックス用）。 */
@Mixin(value = EmiApi.class, remap = false)
public abstract class EmiApiMixin implements WeiHooked {
    @Invoker("setPages")
    private static void wei$setPages(Map<EmiRecipeCategory, List<EmiRecipe>> recipes, EmiIngredient stack) {
        throw new AssertionError();
    }

    @Inject(method = "displayRecipes", at = @At("HEAD"), cancellable = true)
    private static void wei$recipes(EmiIngredient stack, CallbackInfo ci) {
        EmiHooks.mark("recipeOverride");
        var pages = EmiHooks.overrideRecipes(stack, false);
        if (pages != null) {
            wei$setPages(pages, stack);
            ci.cancel();
        }
    }

    @Inject(method = "displayUses", at = @At("HEAD"), cancellable = true)
    private static void wei$uses(EmiIngredient stack, CallbackInfo ci) {
        var pages = EmiHooks.overrideRecipes(stack, true);
        if (pages != null) {
            wei$setPages(pages, stack);
            ci.cancel();
        }
    }
}
