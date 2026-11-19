package io.github.wkwk.wei.emimixin;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;
import io.github.wkwk.wei.emihook.EmiHooks;
import io.github.wkwk.wei.emihook.WeiHooked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 検索欄のハイライト表示で EMI 自身が暗くするのを止める。
 * EMI の暗転は Minecraft 1.21.11 の GUI 描画ではアイテムの下に隠れて見えないため、
 * WEI が Wynntils のスロット描画の後に描き直す（{@code SearchHighlight}）。WEI の検索語もそこで効かせる。
 */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerMixin implements WeiHooked {
    @Redirect(method = "renderSlotOverlays", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/search/EmiSearch$CompiledQuery;test(Ldev/emi/emi/api/stack/EmiStack;)Z"))
    private static boolean wei$test(EmiSearch.CompiledQuery query, EmiStack stack) {
        EmiHooks.mark("highlight");
        return true;
    }
}
