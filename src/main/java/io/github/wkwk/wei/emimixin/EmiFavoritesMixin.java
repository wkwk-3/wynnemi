package io.github.wkwk.wei.emimixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.runtime.EmiFavorites;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.emihook.EmiHooks;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 読めなかったお気に入りは EMI が捨ててしまうので、覚えておいて保存のときに書き戻す */
@Mixin(value = EmiFavorites.class, remap = false)
public abstract class EmiFavoritesMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private static void wei$collectUnresolved(JsonArray arr, CallbackInfo ci) {
        EmiHooks.mark("favorites");
        List<JsonObject> unresolved = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject json = el.getAsJsonObject();
            if (!json.has("stack")) continue;
            try {
                if (EmiIngredientSerializer.getDeserialized(json.get("stack")).isEmpty()) unresolved.add(json.deepCopy());
            } catch (Throwable t) {
                unresolved.add(json.deepCopy());
            }
        }
        EmiHooks.setUnresolvedFavorites(unresolved);
        Wei.LOGGER.info("[WEI diag#1] Favorites in file: {}, unresolved (kept): {}", arr.size(), unresolved.size());
    }

    @Inject(method = "save", at = @At("RETURN"))
    private static void wei$writeBack(CallbackInfoReturnable<JsonArray> cir) {
        JsonArray arr = cir.getReturnValue();
        if (arr == null) return;
        for (JsonObject json : EmiHooks.unresolvedFavorites()) arr.add(json);
    }
}
