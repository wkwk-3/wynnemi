package io.github.wkwk.wei.emi;

import io.github.wkwk.wei.Diag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * お気に入り・履歴の保存形式。キーだけを保存し、読み込み時に登録中のスタックから引く。
 */
public final class WynnEmiStackSerializer implements EmiIngredientSerializer<WynnEmiStack> {
    public static final String TYPE = io.github.wkwk.wei.util.StackKeys.TYPE;
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
                String key = json.get("key").getAsString();
                WynnEmiStack stack = REGISTERED.get(key);
                if (stack == null) {
                    String legacy = legacyKey(key);
                    if (legacy != null) stack = REGISTERED.get(legacy);
                }
                if (stack != null) return stack.copy();
                // 空を返すと EMI はそのお気に入りを破棄する（Mixin で保持）
                Diag.warn(1, "Deserialize MISS for key '{}' (registered stacks: {}, catalog items: {}, thread={})",
                        json.get("key").getAsString(), REGISTERED.size(),
                        io.github.wkwk.wei.data.WynnCatalog.current().entries().size(), Diag.thread());
            }
        }
        return EmiStack.EMPTY;
    }

    /** 旧形式のキーを現行の形に変換する。不要なら null。 */
    public static @org.jetbrains.annotations.Nullable String legacyKey(String key) {
        return io.github.wkwk.wei.util.StackKeys.legacyKey(key, WynnLookup.current().keyByPlainName::get);
    }

    @Override
    public JsonElement serialize(WynnEmiStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("key", stack.wynnKey());
        return json;
    }
}
