package io.github.wkwk.wei.emimixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import io.github.wkwk.wei.emihook.EmiHooks;
import io.github.wkwk.wei.emihook.EmiSearchExtension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** WEI の検索語で絞り込み・並べ替えを足す。 */
@Mixin(targets = "dev.emi.emi.search.EmiSearch$SearchWorker", remap = false)
public abstract class SearchWorkerMixin {
    @Shadow
    @Final
    @Mutable
    private String query;

    @Unique
    private List<EmiSearchExtension.Prepared> wei$extensions = List.of();

    /**
     * WEI の検索語を取り出し、残りを EMI 本体に渡す。
     * EmiSearch.search の排他ブロック内・クライアントスレッドで動くため、他の検索と混ざらない。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void wei$prepare(CallbackInfo ci) {
        EmiHooks.mark("search");
        EmiHooks.PreparedSearch prepared = EmiHooks.prepareSearch(query);
        wei$extensions = prepared.extensions();
        query = prepared.remainingQuery();
    }

    @Redirect(method = "run", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/search/EmiSearch$CompiledQuery;isEmpty()Z"))
    private boolean wei$isEmpty(dev.emi.emi.search.EmiSearch.CompiledQuery compiled) {
        return compiled.isEmpty() && wei$extensions.isEmpty();
    }

    @Redirect(method = "run", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/search/EmiSearch$CompiledQuery;test(Ldev/emi/emi/api/stack/EmiStack;)Z"))
    private boolean wei$test(dev.emi.emi.search.EmiSearch.CompiledQuery compiled, EmiStack stack) {
        if (!compiled.test(stack)) return false;
        for (EmiSearchExtension.Prepared extension : wei$extensions) {
            if (!extension.test(stack)) return false;
        }
        return true;
    }

    /** 絞り込み結果を渡す apply（ordinal 1）の前で並べ替える */
    @ModifyArg(method = "run", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/search/EmiSearch;apply(Ldev/emi/emi/search/EmiSearch$SearchWorker;Ljava/util/List;)V",
            ordinal = 1), index = 1)
    private List<? extends EmiIngredient> wei$sort(List<? extends EmiIngredient> stacks) {
        Comparator<EmiStack> sorter = null;
        for (EmiSearchExtension.Prepared extension : wei$extensions) {
            Comparator<EmiStack> s = extension.sorter();
            if (s != null) sorter = s;
        }
        if (sorter == null) return stacks;
        Comparator<EmiStack> finalSorter = sorter;
        List<EmiIngredient> sorted = new ArrayList<>(stacks);
        sorted.sort((a, b) -> finalSorter.compare(a.getEmiStacks().get(0), b.getEmiStacks().get(0)));
        return List.copyOf(sorted);
    }
}
