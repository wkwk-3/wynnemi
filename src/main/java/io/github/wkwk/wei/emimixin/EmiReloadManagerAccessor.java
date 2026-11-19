package io.github.wkwk.wei.emimixin;

import dev.emi.emi.runtime.EmiReloadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EmiReloadManager.class, remap = false)
public interface EmiReloadManagerAccessor {
    @Accessor("loadedResourcesMask")
    static int wei$getMask() {
        throw new AssertionError();
    }

    @Accessor("loadedResourcesMask")
    static void wei$setMask(int mask) {
        throw new AssertionError();
    }

    @Accessor("clear")
    static boolean wei$isClear() {
        throw new AssertionError();
    }

    @Accessor("restart")
    static boolean wei$isRestart() {
        throw new AssertionError();
    }
}
