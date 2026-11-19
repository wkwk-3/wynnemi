package io.github.wkwk.wei.emimixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.runtime.EmiSidebars;
import io.github.wkwk.wei.emihook.EmiHooks;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 読めなかった検索履歴を保持し、保存時に書き戻す。 */
@Mixin(value = EmiSidebars.class, remap = false)
public abstract class EmiSidebarsMixin {
    @Unique
    private static List<JsonElement> wei$unresolved = new ArrayList<>();

    @Inject(method = "load", at = @At("HEAD"))
    private static void wei$beginLoad(JsonObject json, CallbackInfo ci) {
        EmiHooks.mark("history");
        wei$unresolved = new ArrayList<>();
    }

    /** load 内の getDeserialized は lookup_history の 1 か所だけ */
    @Redirect(method = "load", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/api/stack/serializer/EmiIngredientSerializer;getDeserialized(Lcom/google/gson/JsonElement;)Ldev/emi/emi/api/stack/EmiIngredient;"))
    private static EmiIngredient wei$deserialize(JsonElement element) {
        EmiIngredient ingredient = EmiIngredientSerializer.getDeserialized(element);
        if (ingredient.isEmpty()) wei$unresolved.add(element.deepCopy());
        return ingredient;
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void wei$endLoad(JsonObject json, CallbackInfo ci) {
        EmiHooks.setUnresolvedLookupHistory(wei$unresolved);
        wei$unresolved = new ArrayList<>();
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
