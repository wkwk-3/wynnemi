package io.github.wkwk.wei.emimixin;

import dev.emi.emi.runtime.EmiPersistentData;
import dev.emi.emi.runtime.EmiReloadManager;
import io.github.wkwk.wei.emihook.EmiHooks;
import net.minecraft.network.chat.Component;
import io.github.wkwk.wei.emihook.WeiHooked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** プラグイン登録の直前で WEI の一覧の完成を待つ。待たないとお気に入り・履歴が読めずに落ちる。 */
@Mixin(targets = "dev.emi.emi.runtime.EmiReloadManager$ReloadWorker", remap = false)
public abstract class ReloadWorkerMixin implements WeiHooked {
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Ldev/emi/emi/platform/EmiAgnos;getPlugins()Ljava/util/List;"))
    private void wei$awaitGates(CallbackInfo ci) {
        EmiHooks.mark("loadGate");
        EmiHooks.awaitLoadGates(name -> EmiReloadManager.step(Component.literal("Waiting for " + name)),
                () -> EmiReloadManagerAccessor.wei$isClear() || EmiReloadManagerAccessor.wei$isRestart());
    }

    /** 読み込みの最後（検索・お気に入り・履歴の読み込み後）に、差し込みが効いたかを確かめる */
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Ldev/emi/emi/runtime/EmiReloadLog;bake()V"))
    private void wei$verifyHooks(CallbackInfo ci) {
        EmiHooks.verifyHooks(EmiPersistentData.FILE.exists());
    }
}
