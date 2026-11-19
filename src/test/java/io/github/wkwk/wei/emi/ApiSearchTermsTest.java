package io.github.wkwk.wei.emi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.wkwk.wei.data.ApiFacets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 公式 API 形式・WEI 独自の検索語の解析と照合 */
class ApiSearchTermsTest {
    /** Lv105 の神話の杖（Mage 用）。rawStrength 10、walkSpeed 20、必要 str 40・dex 0 */
    private static final ApiFacets MYTHIC_WAND = new ApiFacets(
            "weapon", "wand", "mythic", "superFast", 105,
            Set.of(),
            Map.of("rawStrength", 10, "walkSpeed", 20, "rawHealth", -500),
            Set.of("hurricaneseye"),
            Map.of("str", 40));

    private static final ApiFacets TIER2_INGREDIENT = new ApiFacets(
            "ingredient", null, "tier_2", null, 80,
            Set.of("cooking", "alchemism"), Map.of(), Set.of());

    private static final ApiFacets ARMOUR = new ApiFacets(
            "armour", "helmet", "legendary", null, 60, Set.of(), Map.of(), Set.of());

    private static ApiSearchTerms terms(String... keyValues) {
        ApiSearchTerms t = new ApiSearchTerms();
        for (int i = 0; i < keyValues.length; i += 2) t.add(keyValues[i], keyValues[i + 1]);
        return t;
    }

    @Test
    void recognizesApiKeysCaseInsensitively() {
        assertTrue(ApiSearchTerms.isApiKey("type"));
        assertTrue(ApiSearchTerms.isApiKey("attackSpeed"));
        assertTrue(ApiSearchTerms.isApiKey("LEVELRANGE"));
        assertTrue(ApiSearchTerms.isApiKey("strReq"));
        assertTrue(ApiSearchTerms.isApiKey("id.rawStrength"));
        assertFalse(ApiSearchTerms.isApiKey("id."));
        assertFalse(ApiSearchTerms.isApiKey("rarity"));
        assertFalse(ApiSearchTerms.isApiKey("level"));
    }

    @Test
    void emptyTermsAreIgnored() {
        ApiSearchTerms t = terms("type", " , ");
        assertTrue(t.isEmpty());
        assertTrue(t.matches(MYTHIC_WAND));
    }

    @Test
    void typeMatchesMainOrSubTypeAndArmorSpelling() {
        assertTrue(terms("type", "weapon").matches(MYTHIC_WAND));
        assertTrue(terms("type", "Wand").matches(MYTHIC_WAND));
        assertFalse(terms("type", "bow").matches(MYTHIC_WAND));
        assertTrue(terms("type", "armor").matches(ARMOUR));
        assertTrue(terms("type", "armour").matches(ARMOUR));
    }

    @Test
    void commaMeansOrAndSeparateTermsMeanAnd() {
        assertTrue(terms("tier", "legendary,mythic").matches(MYTHIC_WAND));
        assertTrue(terms("tier", "legendary,mythic").matches(ARMOUR));
        assertFalse(terms("tier", "rare,unique").matches(MYTHIC_WAND));
        assertTrue(terms("tier", "mythic", "type", "wand").matches(MYTHIC_WAND));
        assertFalse(terms("tier", "mythic", "type", "bow").matches(MYTHIC_WAND));
    }

    @Test
    void ingredientTierAcceptsNumberOrTierPrefix() {
        assertTrue(terms("tier", "2").matches(TIER2_INGREDIENT));
        assertTrue(terms("tier", "tier_2").matches(TIER2_INGREDIENT));
        assertTrue(terms("tier", "Tier 2").matches(TIER2_INGREDIENT));
        assertFalse(terms("tier", "3").matches(TIER2_INGREDIENT));
    }

    @Test
    void attackSpeedIgnoresCaseAndSymbols() {
        assertTrue(terms("attackSpeed", "superFast").matches(MYTHIC_WAND));
        assertTrue(terms("attackspeed", "super_fast").matches(MYTHIC_WAND));
        assertFalse(terms("attackSpeed", "fast").matches(MYTHIC_WAND));
    }

    @Test
    void levelRangeForms() {
        assertTrue(terms("levelRange", "100-105").matches(MYTHIC_WAND));
        assertTrue(terms("levelRange", "105-100").matches(MYTHIC_WAND));
        assertTrue(terms("levelRange", "105").matches(MYTHIC_WAND));
        assertFalse(terms("levelRange", "104").matches(MYTHIC_WAND));
        assertTrue(terms("levelRange", ">=105").matches(MYTHIC_WAND));
        assertFalse(terms("levelRange", ">105").matches(MYTHIC_WAND));
        assertTrue(terms("levelRange", "<=105").matches(MYTHIC_WAND));
        assertFalse(terms("levelRange", "<105").matches(MYTHIC_WAND));
        assertFalse(terms("levelRange", "abc").matches(MYTHIC_WAND));
    }

    @Test
    void levelRangeNeverMatchesItemsWithoutLevel() {
        ApiFacets noLevel = new ApiFacets("material", null, null, null, 0, Set.of(), Map.of(), Set.of());
        assertFalse(terms("levelRange", "0").matches(noLevel));
        assertFalse(terms("levelRange", "<=10").matches(noLevel));
    }

    @Test
    void professionsAndMajorIds() {
        assertTrue(terms("professions", "Cooking").matches(TIER2_INGREDIENT));
        assertFalse(terms("professions", "tailoring").matches(TIER2_INGREDIENT));
        assertTrue(terms("majorIds", "Hurricane's Eye").matches(MYTHIC_WAND));
        assertFalse(terms("majorIds", "madness").matches(MYTHIC_WAND));
    }

    @Test
    void identificationsMatchIgnoringCaseAndRemembersFirstForSorting() {
        ApiSearchTerms t = terms("identifications", "walkSpeed,rawStrength", "identifications", "rawHealth");
        assertTrue(t.matches(MYTHIC_WAND));
        assertEquals("walkspeed", t.firstIdentification());
        assertFalse(terms("identifications", "manaRegen").matches(MYTHIC_WAND));
        assertNull(terms("tier", "mythic").firstIdentification());
    }

    @Test
    void identificationValueRanges() {
        assertTrue(terms("id.rawStrength", "5-15").matches(MYTHIC_WAND));
        assertFalse(terms("id.rawStrength", ">10").matches(MYTHIC_WAND));
        assertTrue(terms("id.walk_speed", ">=20").matches(MYTHIC_WAND));
        assertFalse(terms("id.manaRegen", ">=0").matches(MYTHIC_WAND));
    }

    @Test
    void negativeRanges() {
        assertTrue(terms("id.rawHealth", "-600--400").matches(MYTHIC_WAND));
        assertTrue(terms("id.rawHealth", "-500").matches(MYTHIC_WAND));
        assertFalse(terms("id.rawHealth", "-400--100").matches(MYTHIC_WAND));
        assertTrue(terms("id.rawHealth", "<0").matches(MYTHIC_WAND));
    }

    @Test
    void skillRequirementsTreatMissingAsZeroAndOnlyApplyToGear() {
        assertTrue(terms("strReq", ">=40").matches(MYTHIC_WAND));
        assertTrue(terms("dexReq", "0").matches(MYTHIC_WAND));
        assertFalse(terms("strReq", "<40").matches(MYTHIC_WAND));
        assertTrue(terms("agiReq", "0").matches(ARMOUR));
        assertFalse(terms("strReq", "0").matches(TIER2_INGREDIENT));
    }

    @Test
    void identificationLookupFallsBackToCompactNames() {
        Map<String, Integer> ids = Map.of("Raw Strength", 7);
        assertEquals(7, ApiSearchTerms.identificationValue(ids, "rawstrength"));
        assertTrue(ApiSearchTerms.hasIdentification(ids, "rawstrength"));
        assertNull(ApiSearchTerms.identificationValue(ids, "rawdexterity"));
    }
}
