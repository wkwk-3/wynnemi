package io.github.wkwk.wei.data;

import com.wynntils.models.gear.type.GearAttackSpeed;
import com.wynntils.models.gear.type.GearInfo;
import com.wynntils.models.gear.type.GearMajorId;
import com.wynntils.models.ingredients.type.IngredientInfo;
import com.wynntils.models.profession.type.GatheringToolInfo;
import com.wynntils.models.profession.type.MaterialInfo;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.rewards.type.CharmInfo;
import com.wynntils.models.rewards.type.TomeInfo;
import com.wynntils.models.stats.type.StatPossibleValues;
import com.wynntils.models.stats.type.StatType;
import com.wynntils.utils.type.Pair;
import com.wynntils.utils.type.RangedValue;
import io.github.wkwk.wei.util.Names;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Wynntils のデータを、公式 API のアイテム検索と同じ分類・表記（{@link WynnCatalog.ApiFacets}）に変換する。
 * 値の一覧は API の {@code /v3/item/metadata} の filters に合わせている。
 */
final class ApiFacetsFactory {
    private ApiFacetsFactory() {}

    static WynnCatalog.ApiFacets gear(GearInfo g) {
        String typeName = g.type().name();
        String category = switch (typeName) {
            case "SPEAR", "WAND", "DAGGER", "BOW", "RELIK" -> "weapon";
            case "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS" -> "armour";
            case "RING", "BRACELET", "NECKLACE" -> "accessory";
            default -> null;
        };
        Map<String, Integer> ids = possibleIds(g.variableStats());
        if (g.fixedStats().healthBuff() != 0) ids.put("basehealth", g.fixedStats().healthBuff());
        Set<String> majors = new HashSet<>();
        g.fixedStats().majorIds().map(GearMajorId::name).map(Names::compact).ifPresent(majors::add);
        return new WynnCatalog.ApiFacets(
                category,
                typeName.toLowerCase(Locale.ROOT),
                g.tier().getName().toLowerCase(Locale.ROOT),
                g.fixedStats().attackSpeed().map(GearAttackSpeed::getApiName).orElse(null),
                g.requirements().level(),
                Set.of(),
                Map.copyOf(ids),
                Set.copyOf(majors));
    }

    static WynnCatalog.ApiFacets ingredient(IngredientInfo i) {
        Map<String, Integer> ids = new HashMap<>();
        for (Pair<StatType, RangedValue> stat : i.variableStats()) {
            ids.merge(stat.key().getApiName().toLowerCase(Locale.ROOT), stat.value().high(), Math::max);
        }
        return new WynnCatalog.ApiFacets("ingredient", null, "tier_" + i.tier(), null, i.level(),
                professions(i.professions()), Map.copyOf(ids), Set.of());
    }

    static WynnCatalog.ApiFacets material(MaterialInfo m) {
        return new WynnCatalog.ApiFacets("material", null, null, null, m.level(),
                professions(List.of(m.professionType())), Map.of(), Set.of());
    }

    static WynnCatalog.ApiFacets tool(GatheringToolInfo t) {
        String sub = switch (t.gatheringToolType().name()) {
            case "AXE" -> "axe";
            case "PICKAXE" -> "pickaxe";
            case "FISHING_ROD" -> "rod";
            case "SCYTHE" -> "scythe";
            default -> t.gatheringToolType().name().toLowerCase(Locale.ROOT);
        };
        return new WynnCatalog.ApiFacets("tool", sub, null, null, t.level(),
                professions(List.of(t.gatheringToolType().getProfessionType())), Map.of(), Set.of());
    }

    static WynnCatalog.ApiFacets charm(CharmInfo c) {
        return new WynnCatalog.ApiFacets("charm", null, c.tier().getName().toLowerCase(Locale.ROOT), null,
                c.requirements().level(), Set.of(), Map.copyOf(possibleIds(c.variableStats())), Set.of());
    }

    static WynnCatalog.ApiFacets tome(TomeInfo t) {
        return new WynnCatalog.ApiFacets("tome", camel(t.type().name()), t.tier().getName().toLowerCase(Locale.ROOT),
                null, t.requirements().level(), Set.of(), Map.copyOf(possibleIds(t.variableStats())), Set.of());
    }

    private static Map<String, Integer> possibleIds(@Nullable List<Pair<StatType, StatPossibleValues>> stats) {
        Map<String, Integer> ids = new HashMap<>();
        if (stats == null) return ids;
        for (Pair<StatType, StatPossibleValues> stat : stats) {
            ids.merge(stat.key().getApiName().toLowerCase(Locale.ROOT), stat.value().range().high(), Math::max);
        }
        return ids;
    }

    private static Set<String> professions(@Nullable List<ProfessionType> professions) {
        Set<String> set = new HashSet<>();
        if (professions != null) {
            for (ProfessionType p : professions) {
                set.add(p.name().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(set);
    }

    /** WEAPON_TOME → weaponTome（API の表記） */
    private static String camel(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String word : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            sb.append(sb.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return sb.toString();
    }
}
