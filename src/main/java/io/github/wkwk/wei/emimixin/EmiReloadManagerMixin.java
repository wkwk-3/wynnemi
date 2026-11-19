package io.github.wkwk.wei.emimixin;

import dev.emi.emi.runtime.EmiReloadManager;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.emihook.EmiHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EMI はタグとレシピの両方の同期を受け取ってから読み込むが、Wynncraft はタグしか送らないので読み込まれない。
 * 片方だけ来て 3 秒たっても、もう片方が来なければ読み込む（diag#12）。
 */
@Mixin(value = EmiReloadManager.class, remap = false)
public abstract class EmiReloadManagerMixin {
    @Unique
    private static final long WEI_SYNC_FALLBACK_MS = 3_000;
    @Unique
    private static volatile long wei$generation;

    @Inject(method = "reloadTags", at = @At("TAIL"))
    private static void wei$afterTags(CallbackInfo ci) {
        wei$schedule("tags");
    }

    @Inject(method = "reloadRecipes", at = @At("TAIL"))
    private static void wei$afterRecipes(CallbackInfo ci) {
        wei$schedule("recipes");
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private static void wei$onClear(CallbackInfo ci) {
        wei$generation++;
    }

    @Unique
    private static void wei$schedule(String received) {
        EmiHooks.mark("syncFallback");
        int mask = EmiReloadManagerAccessor.wei$getMask();
        if (mask == 0 || mask == 3) {
            // 両方そろって読み込みが始まった
            wei$generation++;
            return;
        }
        long generation = ++wei$generation;
        Wei.LOGGER.info("[WEI diag#12] Only the {} signal arrived; reloading EMI in {}ms unless the other one comes",
                received, WEI_SYNC_FALLBACK_MS);
        Thread timer = new Thread(() -> {
            try {
                Thread.sleep(WEI_SYNC_FALLBACK_MS);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (EmiReloadManager.class) {
                int now = EmiReloadManagerAccessor.wei$getMask();
                if (generation != wei$generation || now == 0 || now == 3) return;
                Wei.LOGGER.info("[WEI diag#12] Sync fallback FIRED (mask={}) -> reload", now);
                EmiReloadManagerAccessor.wei$setMask(0);
                wei$generation++;
            }
            EmiReloadManager.reload();
        }, "WEI EMI sync fallback");
        timer.setDaemon(true);
        timer.start();
    }
}
