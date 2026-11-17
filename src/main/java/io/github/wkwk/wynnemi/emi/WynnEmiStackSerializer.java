package io.github.wkwk.wynnemi.emi;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EMI のお気に入りや履歴に Wynncraft のアイテムを保存できるようにする。
 * 保存するのはキーだけで、読み込み時に現在登録中のスタックから引く。
 */
public final class WynnEmiStackSerializer implements EmiIngredientSerializer<WynnEmiStack> {
    public static final String TYPE = "wynnemi";
    private static final Map<String, WynnEmiStack> REGISTERED = new ConcurrentHashMap<>();

    static void clear() {
        REGISTERED.clear();
    }

    static void remember(WynnEmiStack stack) {
        REGISTERED.put(stack.wynnKey(), stack);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public EmiIngredient deserialize(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            if (json.has("key")) {
                WynnEmiStack stack = REGISTERED.get(json.get("key").getAsString());
                if (stack != null) return stack.copy();
            }
        }
        return EmiStack.EMPTY;
    }

    @Override
    public JsonElement serialize(WynnEmiStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("key", stack.wynnKey());
        return json;
    }
}
