package io.github.wkwk.wei.emimixin;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;
import io.github.wkwk.wei.emihook.EmiHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 検索欄のハイライト表示にも WEI の条件を効かせる。
 * EMI は EmiSearch.compiledQuery（WEI の語を除いた残り）だけで判定するため。
 */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerMixin {
    /** この描画での判定表。WEI の条件が無ければ null */
    @Unique
    private static Map<EmiStack, Boolean> wei$highlight;

    @Inject(method = "renderSlotOverlays", at = @At("HEAD"))
    private static void wei$prepareHighlight(CallbackInfo ci) {
        wei$highlight = null;
        if (EmiScreenManager.search == null || !EmiScreenManager.search.highlight) return;
        EmiSearch.CompiledQuery query = EmiSearch.compiledQuery;
        var screen = EmiApi.getHandledScreen();
        if (query == null || screen == null) return;
        EmiHooks.mark("highlight");
        List<ItemStack> items = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) items.add(slot.getItem());
        wei$highlight = EmiHooks.highlightResults(query, items);
    }

    @Redirect(method = "renderSlotOverlays", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/search/EmiSearch$CompiledQuery;test(Ldev/emi/emi/api/stack/EmiStack;)Z"))
    private static boolean wei$test(EmiSearch.CompiledQuery query, EmiStack stack) {
        if (!query.test(stack)) return false;
        Map<EmiStack, Boolean> results = wei$highlight;
        return results == null || results.getOrDefault(stack, false);
    }
}
