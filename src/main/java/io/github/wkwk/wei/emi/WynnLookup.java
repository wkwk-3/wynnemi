package io.github.wkwk.wei.emi;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * EMI に登録した内容の索引。EMI の読み込みスレッドで作って丸ごと差し替え、
 * クライアントスレッドから読む。
 */
public final class WynnLookup {
    /** 未鑑定ボックスの系統: EMI 上のスタックとレベル順の候補レシピ */
    record BoxIndex(WynnEmiStack stack, List<BoxCandidate> candidates) {
        /** 範囲内の最初の候補（レシピ画面の初期位置） */
        @Nullable WynnLinkRecipe firstInRange(int low, int high) {
            for (BoxCandidate c : candidates) {
                if (c.level() >= low && c.level() <= high) return c.recipe();
            }
            return null;
        }
    }

    record BoxCandidate(int level, WynnLinkRecipe recipe) {}

    private static volatile WynnLookup current = new WynnLookup(Map.of(), Map.of(), Map.of(), Map.of());

    final Map<String, WynnEmiStack> byKey;
    /** 色・アイコンを除いた小文字の名前 → キー（名前で引く最後の手段） */
    final Map<String, String> keyByPlainName;
    final Map<String, String> favoriteNameByKey;
    final Map<String, BoxIndex> boxes;

    WynnLookup(Map<String, WynnEmiStack> byKey, Map<String, String> keyByPlainName,
            Map<String, String> favoriteNameByKey, Map<String, BoxIndex> boxes) {
        this.byKey = byKey;
        this.keyByPlainName = keyByPlainName;
        this.favoriteNameByKey = favoriteNameByKey;
        this.boxes = boxes;
    }

    static WynnLookup current() {
        return current;
    }

    static void publish(WynnLookup lookup) {
        WynnBoxInstances.clear();
        current = lookup;
    }

    /** EMI の登録で索引ができているか */
    public static boolean isReady() {
        return !current.byKey.isEmpty();
    }

    /** Wynncraft のアイテムなら Wynntils でのお気に入り名、それ以外は null */
    public static @Nullable String favoriteNameOf(Object emiIngredient) {
        if (emiIngredient instanceof WynnEmiStack stack) {
            return current.favoriteNameByKey.get(stack.wynnKey());
        }
        return null;
    }
}
