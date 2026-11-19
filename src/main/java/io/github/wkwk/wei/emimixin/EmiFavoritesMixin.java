package io.github.wkwk.wei.emimixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.runtime.EmiFavorites;
import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.emihook.EmiHooks;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 読めなかったお気に入りを保持し、保存時に書き戻す（EMI は捨てる）。 */
@Mixin(value = EmiFavorites.class, remap = false)
public abstract class EmiFavoritesMixin {
    /** load 中だけ使う: stack 要素 → お気に入り 1 件の JSON */
    @Unique
    private static Map<JsonElement, JsonObject> wei$owners = Map.of();
    @Unique
    private static List<JsonObject> wei$unresolved = new ArrayList<>();

    @Inject(method = "load", at = @At("HEAD"))
    private static void wei$beginLoad(JsonArray arr, CallbackInfo ci) {
        EmiHooks.mark("favorites");
        Map<JsonElement, JsonObject> owners = new IdentityHashMap<>();
        for (JsonElement el : arr) {
            if (el.isJsonObject() && el.getAsJsonObject().has("stack")) {
                owners.put(el.getAsJsonObject().get("stack"), el.getAsJsonObject());
            }
        }
        wei$owners = owners;
        wei$unresolved = new ArrayList<>();
    }

    @Redirect(method = "load", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/api/stack/serializer/EmiIngredientSerializer;getDeserialized(Lcom/google/gson/JsonElement;)Ldev/emi/emi/api/stack/EmiIngredient;"))
    private static EmiIngredient wei$deserialize(JsonElement element) {
        EmiIngredient ingredient = EmiIngredientSerializer.getDeserialized(element);
        if (ingredient.isEmpty()) {
            JsonObject owner = wei$owners.get(element);
            if (owner != null) wei$unresolved.add(owner.deepCopy());
        }
        return ingredient;
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void wei$endLoad(JsonArray arr, CallbackInfo ci) {
        EmiHooks.setUnresolvedFavorites(wei$unresolved);
        Diag.log(1, "Favorites in file: {}, unresolved (kept): {}", arr.size(), wei$unresolved.size());
        wei$owners = Map.of();
        wei$unresolved = new ArrayList<>();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private static void wei$writeBack(CallbackInfoReturnable<JsonArray> cir) {
        JsonArray arr = cir.getReturnValue();
        if (arr == null) return;
        for (JsonObject json : EmiHooks.unresolvedFavorites()) arr.add(json);
    }
}
