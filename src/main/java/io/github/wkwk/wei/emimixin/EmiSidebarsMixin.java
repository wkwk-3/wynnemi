package io.github.wkwk.wei.emimixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.runtime.EmiSidebars;
import io.github.wkwk.wei.emihook.EmiHooks;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 検索履歴も、読めなかったものを覚えておいて保存のときに書き戻す */
@Mixin(value = EmiSidebars.class, remap = false)
public abstract class EmiSidebarsMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private static void wei$collectUnresolved(JsonObject json, CallbackInfo ci) {
        EmiHooks.mark("history");
        List<JsonElement> unresolved = new ArrayList<>();
        if (json.has("lookup_history") && json.get("lookup_history").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("lookup_history")) {
                try {
                    if (EmiIngredientSerializer.getDeserialized(el).isEmpty()) unresolved.add(el.deepCopy());
                } catch (Throwable t) {
                    unresolved.add(el.deepCopy());
                }
            }
        }
        EmiHooks.setUnresolvedLookupHistory(unresolved);
    }

    @Inject(method = "save", at = @At("TAIL"))
    private static void wei$writeBack(JsonObject json, CallbackInfo ci) {
        if (!json.has("lookup_history") || !json.get("lookup_history").isJsonArray()) return;
        JsonArray arr = json.getAsJsonArray("lookup_history");
        for (JsonElement el : EmiHooks.unresolvedLookupHistory()) {
            if (arr.size() >= 1024) break;
            arr.add(el);
        }
    }
}
