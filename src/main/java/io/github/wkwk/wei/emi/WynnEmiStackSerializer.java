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
 * EMI のお気に入りや履歴に Wynncraft のアイテムを保存できるようにする。
 * 保存するのはキーだけで、読み込み時に現在登録中のスタックから引く。
 */
public final class WynnEmiStackSerializer implements EmiIngredientSerializer<WynnEmiStack> {
    public static final String TYPE = "wei";
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
                // 1.0.1 でアスペクトをティアごと（aspect/名前 T2）から1件（aspect/名前）にまとめた。古いキーも読めるようにする
                if (stack == null) {
                    String legacy = legacyKey(key);
                    if (legacy != null) stack = REGISTERED.get(legacy);
                }
                if (stack != null) return stack.copy();
                // diag#1: ここで空を返すと EMI はそのお気に入り（非表示設定など）を捨てる
                Diag.warn(1, "Deserialize MISS for key '{}' (registered stacks: {}, catalog items: {}, thread={})",
                        json.get("key").getAsString(), REGISTERED.size(),
                        io.github.wkwk.wei.data.WynnCatalog.current().entries().size(), Diag.thread());
            }
        }
        return EmiStack.EMPTY;
    }

    /** 古い形式のキーを今の形にする。変える必要が無ければ null */
    public static @org.jetbrains.annotations.Nullable String legacyKey(String key) {
        if (key.startsWith("aspect/") && key.matches(".* T\\d+$")) return key.substring(0, key.lastIndexOf(" T"));
        return null;
    }

    @Override
    public JsonElement serialize(WynnEmiStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("key", stack.wynnKey());
        return json;
    }
}
