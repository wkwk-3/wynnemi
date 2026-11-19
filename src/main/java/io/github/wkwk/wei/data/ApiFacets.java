package io.github.wkwk.wei.data;

import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * 公式 API のアイテム検索と同じ分類。値は API の表記（小文字・camelCase）。
 */
public record ApiFacets(
        /** weapon / armour / accessory / tome / charm / tool / ingredient / material */
        @Nullable String type,
        /** 武器種・防具部位・アクセサリー種・トーム種・ツール種 */
        @Nullable String subType,
        /** 装備: normal〜mythic、素材: tier_0〜tier_3 */
        @Nullable String tier,
        /** superSlow〜superFast */
        @Nullable String attackSpeed,
        int level,
        Set<String> professions,
        /** ID 名（小文字）→ 最大値 */
        Map<String, Integer> identifications,
        /** 英数字だけの小文字 */
        Set<String> majorIds,
        /** str / dex / int / def / agi → 必要ポイント（0 は含めない） */
        Map<String, Integer> skillRequirements) {
    public static final ApiFacets NONE = new ApiFacets(null, null, null, null, 0, Set.of(), Map.of(), Set.of());

    public ApiFacets(@Nullable String type, @Nullable String subType, @Nullable String tier, @Nullable String attackSpeed,
            int level, Set<String> professions, Map<String, Integer> identifications, Set<String> majorIds) {
        this(type, subType, tier, attackSpeed, level, professions, identifications, majorIds, Map.of());
    }
}
