package io.github.wkwk.wei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeiConfigTest {
    private static WeiConfig parse(String json) {
        return WeiConfig.fromJson(new StringReader(json));
    }

    @Test
    void oldDiagnosticsKeyIsIgnored() {
        // 旧版は既定値 true を保存していた。リリース版でログが出続けないこと
        assertNull(parse("{\"diagnostics\": true}").diagnosticsOverride);
        assertEquals(Boolean.FALSE, parse("{\"diagnosticsOverride\": false}").diagnosticsOverride);
    }

    @Test
    void oldDefaultColorMigrates() {
        assertEquals(0xFF00FF66, parse("{\"lootHighlightColor\": " + 0xA0FFAA00 + "}").lootHighlightColor);
        assertEquals(0xFF123456, parse("{\"lootHighlightColor\": " + 0xFF123456 + "}").lootHighlightColor);
    }

    @Test
    void emptyAndBrokenFieldsGetDefaults() {
        WeiConfig empty = parse("");
        assertTrue(empty.lootRules.isEmpty());
        assertEquals("lowest", empty.lootHighlightPrice);
        WeiConfig broken = parse("{\"lootRules\": [null, {\"thresholdLe\": 3}], \"lootHighlightPrice\": null}");
        assertEquals(1, broken.lootRules.size());
        assertEquals("any", broken.lootRules.get(0).type);
        assertEquals("any", broken.lootRules.get(0).rarity);
        assertEquals("lowest", broken.lootHighlightPrice);
        assertEquals(3, broken.thresholdFor("gear", "mythic"));
    }

    @Test
    void mostSpecificRuleWins() {
        WeiConfig c = new WeiConfig();
        c.lootHighlightThresholdLe = 1.5;
        c.lootRules = List.of(
                new WeiConfig.LootRule("any", "mythic", 10),
                new WeiConfig.LootRule("gearBox", "any", 5),
                new WeiConfig.LootRule("gearBox", "mythic", 20),
                new WeiConfig.LootRule("ingredient", "tier3", -1));
        assertEquals(20, c.thresholdFor("gearBox", "mythic"));
        // 種類の一致はレアリティの一致より強い
        assertEquals(5, c.thresholdFor("gearBox", "legendary"));
        assertEquals(10, c.thresholdFor("gear", "mythic"));
        // 大文字小文字は区別しない
        assertEquals(20, c.thresholdFor("GEARBOX", "Mythic"));
        // どれにも当たらなければ全体の基準
        assertEquals(1.5, c.thresholdFor("gear", "rare"));
        // 負の基準（通知しない）もそのまま返す
        assertEquals(-1, c.thresholdFor("ingredient", "tier3"));
    }

    @Test
    void laterRuleWinsOnTie() {
        WeiConfig c = new WeiConfig();
        c.lootRules = List.of(new WeiConfig.LootRule("gear", "any", 2), new WeiConfig.LootRule("gear", "any", 7));
        assertEquals(7, c.thresholdFor("gear", "rare"));
    }
}
