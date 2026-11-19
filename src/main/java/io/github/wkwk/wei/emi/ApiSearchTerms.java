package io.github.wkwk.wei.emi;

import io.github.wkwk.wei.data.ApiFacets;
import io.github.wkwk.wei.util.Names;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * 公式 API のアイテム検索（POST /v3/item/search）と同じキーでの絞り込み。
 * https://docs.wynncraft.com/modules/item-recipe/search-items
 *
 * <pre>
 *   type:weapon        type:wand（詳細な種類も可）
 *   tier:mythic        tier:tier_3（素材は 0〜3 / tier_0〜tier_3）
 *   attackSpeed:superFast
 *   levelRange:100-105 levelRange:&gt;=100 levelRange:105
 *   professions:cooking
 *   identifications:rawStrength
 *   majorIds:hurricanesEye
 * </pre>
 *
 * WEI 独自:
 *   strReq:20-50 dexReq:>=40 intReq:<=10 defReq:0 agiReq:30   必要スキル（不要は 0）
 *   id.rawStrength:5-15  id.walkSpeed:>=20                    ID の最大値の範囲
 *
 * カンマ区切りは OR、語どうしは AND。大小文字と記号は区別しない。
 * identifications を指定すると最初の ID の降順に並ぶ（公式サイトと同じ）。
 */
final class ApiSearchTerms {
    private static final Set<String> KEYS = Set.of(
            "type", "tier", "attackspeed", "levelrange", "professions", "identifications", "majorids");

    private final List<Term> terms = new ArrayList<>();
    private @Nullable String firstIdentification;

    private record Term(String key, List<String> values) {}

    private static final Set<String> SKILL_KEYS = Set.of("strreq", "dexreq", "intreq", "defreq", "agireq");

    static boolean isApiKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return KEYS.contains(k) || SKILL_KEYS.contains(k) || (k.startsWith("id.") && k.length() > 3);
    }

    /** 範囲指定のキー（値を正規化しない） */
    private static boolean isRangeKey(String k) {
        return k.equals("levelrange") || SKILL_KEYS.contains(k) || k.startsWith("id.");
    }

    void add(String key, String rawValue) {
        String k = key.toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        for (String v : rawValue.split(",")) {
            if (!v.isBlank()) values.add(isRangeKey(k) ? v.trim() : Names.compact(v));
        }
        if (values.isEmpty()) return;
        if (k.equals("identifications") && firstIdentification == null) firstIdentification = values.get(0);
        terms.add(new Term(k, values));
    }

    boolean isEmpty() {
        return terms.isEmpty();
    }

    /** 並び替えに使う最初の ID。無ければ null。 */
    @Nullable String firstIdentification() {
        return firstIdentification;
    }

    boolean matches(ApiFacets f) {
        for (Term term : terms) {
            if (!matchesAny(term, f)) return false;
        }
        return true;
    }

    private static boolean matchesAny(Term term, ApiFacets f) {
        for (String value : term.values()) {
            if (matchesOne(term.key(), value, f)) return true;
        }
        return false;
    }

    private static boolean matchesOne(String key, String value, ApiFacets f) {
        if (SKILL_KEYS.contains(key)) {
            // 不要は 0。装備以外は対象外
            if (!"weapon".equals(f.type()) && !"armour".equals(f.type()) && !"accessory".equals(f.type())) return false;
            return inRange(f.skillRequirements().getOrDefault(key.substring(0, 3), 0), value);
        }
        if (key.startsWith("id.")) {
            Integer v = identificationValue(f.identifications(), Names.compact(key.substring(3)));
            return v != null && inRange(v, value);
        }
        return switch (key) {
            case "type" -> value.equals(compact(f.type())) || value.equals(compact(f.subType()))
                    // armor / armour
                    || (value.equals("armor") && "armour".equals(f.type()));
            case "tier" -> {
                String tier = compact(f.tier());
                yield value.equals(tier) || ("tier" + value).equals(tier);
            }
            case "attackspeed" -> value.equals(compact(f.attackSpeed()));
            case "levelrange" -> f.level() > 0 && inRange(f.level(), value);
            case "professions" -> f.professions().contains(value);
            case "identifications" -> hasIdentification(f.identifications(), value);
            case "majorids" -> f.majorIds().contains(value);
            default -> false;
        };
    }

    /** ID 名は記号を除いて比較する */
    static boolean hasIdentification(Map<String, Integer> ids, String compactName) {
        return identificationValue(ids, compactName) != null;
    }

    static @Nullable Integer identificationValue(Map<String, Integer> ids, String compactName) {
        Integer direct = ids.get(compactName);
        if (direct != null) return direct;
        for (Map.Entry<String, Integer> e : ids.entrySet()) {
            if (Names.compact(e.getKey()).equals(compactName)) return e.getValue();
        }
        return null;
    }

    /** 100-105 / 105 / >=100 / >100 / <=105 / <105 */
    private static boolean inRange(int level, String spec) {
        try {
            if (spec.startsWith(">=")) return level >= Integer.parseInt(spec.substring(2));
            if (spec.startsWith("<=")) return level <= Integer.parseInt(spec.substring(2));
            if (spec.startsWith(">")) return level > Integer.parseInt(spec.substring(1));
            if (spec.startsWith("<")) return level < Integer.parseInt(spec.substring(1));
            int dash = spec.indexOf('-', 1);
            // 負の数どうし（-20--5）
            while (dash > 0 && spec.charAt(dash - 1) == '-') dash = spec.indexOf('-', dash + 1);
            if (dash > 0) {
                int low = Integer.parseInt(spec.substring(0, dash));
                int high = Integer.parseInt(spec.substring(dash + 1));
                return level >= Math.min(low, high) && level <= Math.max(low, high);
            }
            return level == Integer.parseInt(spec);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String compact(@Nullable String s) {
        return s == null ? "" : Names.compact(s);
    }
}
