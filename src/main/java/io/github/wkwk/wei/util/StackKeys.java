package io.github.wkwk.wei.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/** お気に入り・履歴に保存されるスタックのキーの扱い。Minecraft・EMI に依存しない（単体テスト用）。 */
public final class StackKeys {
    /** 保存形式の type */
    public static final String TYPE = "wei";
    /** 手持ちの未鑑定ボックス用の一時スタック。保存しない */
    public static final String BOX_INSTANCE_PREFIX = "boxinstance/";

    /** 名前の無いアイテムのグループ（キーを内部名で作る） */
    private static final Set<String> UNNAMED_GROUPS = Set.of("powder", "rune", "ward", "augment", "emerald", "dungeon_key");

    private StackKeys() {}

    /** 保存済みの JSON が一時スタックか */
    public static boolean isBoxInstanceJson(@Nullable JsonElement json) {
        if (json == null || !json.isJsonObject()) return false;
        JsonObject obj = json.getAsJsonObject();
        return TYPE.equals(string(obj, "type")) && startsWith(string(obj, "key"), BOX_INSTANCE_PREFIX);
    }

    /**
     * 旧形式のキーを現行の形に変換する。不要なら null。
     * 3.0.0 で削除予定（2.x の間に保存し直されるため）。
     *
     * @param keyByPlainName 記号を除いた小文字の表示名 → 現行キー
     */
    public static @Nullable String legacyKey(String key, Function<String, String> keyByPlainName) {
        // 旧: aspect/名前 T2（1.0.1 でティアを 1 件に統合）
        if (key.startsWith("aspect/") && key.matches(".* T\\d+$")) return key.substring(0, key.lastIndexOf(" T"));
        // 旧: 名前の無いアイテムのキーが表示名（rune/Az Rune → 現 rune/AZ）。表示名から現行キーを引く
        int slash = key.indexOf('/');
        if (slash <= 0) return null;
        String group = key.substring(0, slash);
        if (!UNNAMED_GROUPS.contains(group)) return null;
        String name = key.substring(slash + 1).replaceAll("#\\d+$", "");
        String now = keyByPlainName.apply(Names.strip(name).toLowerCase(Locale.ROOT));
        return now != null && now.startsWith(group + "/") && !now.equals(key) ? now : null;
    }

    private static @Nullable String string(JsonObject obj, String name) {
        JsonElement e = obj.get(name);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static boolean startsWith(@Nullable String s, String prefix) {
        return s != null && s.startsWith(prefix);
    }
}
