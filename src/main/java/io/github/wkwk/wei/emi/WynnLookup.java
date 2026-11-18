package io.github.wkwk.wei.emi;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * EMI に登録した内容の索引。EMI の読み込み（別スレッド）で作って丸ごと差し替え、
 * 手持ちアイテムの判定やお気に入りの連動（クライアントスレッド）から読む。
 */
public final class WynnLookup {
    /** 未鑑定ボックスの系統：EMI 上のスタックと、レベル順の候補レシピ */
    record BoxIndex(WynnEmiStack stack, List<BoxCandidate> candidates) {
        /** 範囲に入る最初の候補（レシピ画面でそこへ飛ぶ） */
        @Nullable WynnLinkRecipe firstInRange(int low, int high) {
            for (BoxCandidate c : candidates) {
                if (c.level() >= low && c.level() <= high) return c.recipe();
            }
            return null;
        }
    }

    record BoxCandidate(int level, WynnLinkRecipe recipe) {}

    private static volatile WynnLookup current = new WynnLookup(Map.of(), Map.of(), Map.of(), Map.of());

    /** カタログのキー → 登録したスタック */
    final Map<String, WynnEmiStack> byKey;
    /** 色やアイコンを除いた名前（小文字）→ カタログのキー（名前で引く最後の手段） */
    final Map<String, String> keyByPlainName;
    /** カタログのキー → Wynntils のお気に入り名 */
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

    /** 診断用：EMI の登録で索引ができているか */
    public static boolean isReady() {
        return !current.byKey.isEmpty();
    }

    /** お気に入りの連動用：EMI のスタックが Wynncraft のアイテムなら、その Wynntils でのお気に入り名 */
    public static @Nullable String favoriteNameOf(Object emiIngredient) {
        if (emiIngredient instanceof WynnEmiStack stack) {
            return current.favoriteNameByKey.get(stack.wynnKey());
        }
        return null;
    }
}
