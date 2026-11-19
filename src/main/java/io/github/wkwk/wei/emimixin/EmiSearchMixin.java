package io.github.wkwk.wei.emimixin;

import dev.emi.emi.search.EmiSearch;
import io.github.wkwk.wei.emihook.EmiHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 検索の前に WEI の検索語を取り出す（クライアントスレッド）。残りの語だけを EMI 本体に渡す */
@Mixin(value = EmiSearch.class, remap = false)
public abstract class EmiSearchMixin {
    @ModifyVariable(method = "search", at = @At("HEAD"), argsOnly = true)
    private static String wei$prepare(String query) {
        EmiHooks.mark("search");
        EmiHooks.PreparedSearch prepared = EmiHooks.prepareSearch(query);
        EmiHooks.pendingSearch = prepared;
        return prepared.remainingQuery();
    }
}
