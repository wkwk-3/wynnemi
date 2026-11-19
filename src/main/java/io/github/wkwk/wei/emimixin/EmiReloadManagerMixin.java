package io.github.wkwk.wei.emimixin;

import dev.emi.emi.runtime.EmiReloadManager;
import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.emihook.EmiHooks;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EMI はタグとレシピ両方の同期で読み込むが、Wynncraft はタグしか送らない。
 * 片方だけ来て 3 秒以内にもう片方が来なければ読み込む（diag#12）。
 * その後 10 秒以内に届いた片割れは数えない（数えると次の合図で二重に読み込む）。
 */
@Mixin(value = EmiReloadManager.class, remap = false)
public abstract class EmiReloadManagerMixin {
    @Unique
    private static final long WEI_SYNC_FALLBACK_MS = 3_000;
    @Unique
    private static final long WEI_LATE_SIGNAL_MS = 10_000;
    @Unique
    private static final ScheduledExecutorService WEI_TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "WEI EMI sync fallback");
        t.setDaemon(true);
        return t;
    });
    @Unique
    private static volatile long wei$generation;
    @Unique
    private static volatile long wei$fallbackFiredAt;

    @Inject(method = "reloadTags", at = @At("TAIL"))
    private static void wei$afterTags(CallbackInfo ci) {
        wei$onSignal("tags");
    }

    @Inject(method = "reloadRecipes", at = @At("TAIL"))
    private static void wei$afterRecipes(CallbackInfo ci) {
        wei$onSignal("recipes");
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private static void wei$onClear(CallbackInfo ci) {
        wei$generation++;
        wei$fallbackFiredAt = 0;
    }

    @Unique
    private static void wei$onSignal(String received) {
        EmiHooks.mark("syncFallback");
        synchronized (EmiReloadManager.class) {
            int mask = EmiReloadManagerAccessor.wei$getMask();
            if (mask == 0 || mask == 3) {
                // EMI 自身が読み込みを始めた
                wei$generation++;
                wei$fallbackFiredAt = 0;
                return;
            }
            if (wei$fallbackFiredAt != 0 && System.currentTimeMillis() - wei$fallbackFiredAt < WEI_LATE_SIGNAL_MS) {
                Diag.log(12, "Late {} signal after the sync fallback; ignored", received);
                EmiReloadManagerAccessor.wei$setMask(0);
                wei$fallbackFiredAt = 0;
                return;
            }
            long generation = ++wei$generation;
            Diag.log(12, "Only the {} signal arrived; reloading EMI in {}ms unless the other one comes",
                    received, WEI_SYNC_FALLBACK_MS);
            WEI_TIMER.schedule(() -> wei$fire(generation), WEI_SYNC_FALLBACK_MS, TimeUnit.MILLISECONDS);
        }
    }

    @Unique
    private static void wei$fire(long generation) {
        synchronized (EmiReloadManager.class) {
            int now = EmiReloadManagerAccessor.wei$getMask();
            if (generation != wei$generation || now == 0 || now == 3) return;
            Diag.log(12, "Sync fallback FIRED (mask={}) -> reload", now);
            EmiReloadManagerAccessor.wei$setMask(0);
            wei$generation++;
            wei$fallbackFiredAt = System.currentTimeMillis();
        }
        EmiReloadManager.reload();
    }
}
