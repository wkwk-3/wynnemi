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
        if (key.startsWith("aspect/") && key.matches(".* T\\d+$")) return key.substring(0, key.lastIndexOf(" T"));
        // 旧: aspect/名前 T2（1.0.1 でティアを 1 件に統合）
        // 旧: 名前の無いアイテムのキーが表示名（rune/Az Rune → 現 rune/AZ）。表示名から現行キーを引く
        int slash = key.indexOf('/');
        if (slash > 0) {
            String group = key.substring(0, slash);
            if (UNNAMED_GROUPS.contains(group)) {
                String name = key.substring(slash + 1).replaceAll("#\\d+$", "");
                String now = WynnLookup.current().keyByPlainName.get(
                        io.github.wkwk.wei.util.Names.strip(name).toLowerCase(java.util.Locale.ROOT));
                if (now != null && now.startsWith(group + "/") && !now.equals(key)) return now;
            }
        }
        return null;
    }

    private static final java.util.Set<String> UNNAMED_GROUPS =
            java.util.Set.of("powder", "rune", "ward", "augment", "emerald", "dungeon_key");

    @Override
    public JsonElement serialize(WynnEmiStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("key", stack.wynnKey());
        return json;
    }
}
