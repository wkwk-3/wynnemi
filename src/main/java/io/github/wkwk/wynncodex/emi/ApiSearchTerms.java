package io.github.wkwk.wynncodex.emi;

import io.github.wkwk.wynncodex.data.WynnCatalog;
import io.github.wkwk.wynncodex.util.Names;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * 公式 API のアイテム検索（POST /v3/item/search）と同じキーで絞り込む。
 * https://docs.wynncraft.com/modules/item-recipe/search-items
 *
 * <pre>
 *   type:weapon        type:wand（武器種などの詳細も type で指定できる）
 *   tier:mythic        tier:tier_3（素材は 0〜3 / tier_0〜tier_3）
 *   attackSpeed:superFast
 *   levelRange:100-105 levelRange:&gt;=100 levelRange:105
 *   professions:cooking
 *   identifications:rawStrength
 *   majorIds:hurricanesEye
 * </pre>
 *
 * 値はカンマ区切りで「または」（API と同じく一覧を渡せる）。違うキー・同じキーの複数指定は「かつ」。
 * 大文字小文字と記号は区別しない。identifications を指定すると、サイトと同じく最初の ID の値が大きい順に並ぶ。
 */
final class ApiSearchTerms {
    private static final Set<String> KEYS = Set.of(
            "type", "tier", "attackspeed", "levelrange", "professions", "identifications", "majorids");

    private final List<Term> terms = new ArrayList<>();
    private @Nullable String firstIdentification;

    private record Term(String key, List<String> values) {}

    static boolean isApiKey(String key) {
        return KEYS.contains(key.toLowerCase(Locale.ROOT));
    }

    void add(String key, String rawValue) {
        String k = key.toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        for (String v : rawValue.split(",")) {
            if (!v.isBlank()) values.add(k.equals("levelrange") ? v.trim() : Names.compact(v));
        }
        if (values.isEmpty()) return;
        if (k.equals("identifications") && firstIdentification == null) firstIdentification = values.get(0);
        terms.add(new Term(k, values));
    }

    boolean isEmpty() {
        return terms.isEmpty();
    }

    /** 最初に指定した ID（並び替えに使う）。無ければ null */
    @Nullable String firstIdentification() {
        return firstIdentification;
    }

    boolean matches(WynnCatalog.ApiFacets f) {
        for (Term term : terms) {
            if (!matchesAny(term, f)) return false;
        }
        return true;
    }

    private static boolean matchesAny(Term term, WynnCatalog.ApiFacets f) {
        for (String value : term.values()) {
            if (matchesOne(term.key(), value, f)) return true;
        }
        return false;
    }

    private static boolean matchesOne(String key, String value, WynnCatalog.ApiFacets f) {
        return switch (key) {
            case "type" -> value.equals(compact(f.type())) || value.equals(compact(f.subType()))
                    // API の表記揺れ（armor / armour）
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

    /** ID の API 名（rawStrength など）は小文字で持っているので、記号を除いて比べる */
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
