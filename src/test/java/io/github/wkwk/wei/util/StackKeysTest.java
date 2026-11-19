package io.github.wkwk.wei.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StackKeysTest {
    private static final Map<String, String> BY_NAME = Map.of(
            "az rune", "rune/AZ",
            "earth powder i", "powder/EARTH_1",
            "corkian amplifier", "gear/Corkian Amplifier");

    private static String legacy(String key) {
        return StackKeys.legacyKey(key, BY_NAME::get);
    }

    @Test
    void aspectTierSuffixIsDropped() {
        assertEquals("aspect/Aspect of the Beastmaster", legacy("aspect/Aspect of the Beastmaster T2"));
        assertNull(legacy("aspect/Aspect of the Beastmaster"));
    }

    @Test
    void unnamedGroupsAreLookedUpByDisplayName() {
        assertEquals("rune/AZ", legacy("rune/Az Rune"));
        // 同名の連番（#2）は外して引く
        assertEquals("powder/EARTH_1", legacy("powder/Earth Powder I#2"));
    }

    @Test
    void currentOrUnknownKeysAreLeftAlone() {
        // すでに現行のキー
        assertNull(legacy("rune/AZ"));
        // 名前の無いグループ以外は変換しない
        assertNull(legacy("gear/Corkian Amplifier"));
        // 引けない名前
        assertNull(legacy("rune/Unknown Rune"));
        // グループをまたぐ結果は採らない
        assertNull(legacy("ward/Corkian Amplifier"));
        assertNull(legacy("noslash"));
    }

    @Test
    void boxInstanceJsonIsTransient() {
        assertTrue(StackKeys.isBoxInstanceJson(JsonParser.parseString(
                "{\"type\":\"wei\",\"key\":\"boxinstance/gear/wand/legendary/95-100#3\"}")));
        assertFalse(StackKeys.isBoxInstanceJson(JsonParser.parseString("{\"type\":\"wei\",\"key\":\"gear/Corkian Amplifier\"}")));
        assertFalse(StackKeys.isBoxInstanceJson(JsonParser.parseString("{\"type\":\"item\",\"key\":\"boxinstance/x\"}")));
        assertFalse(StackKeys.isBoxInstanceJson(JsonParser.parseString("{\"type\":\"wei\"}")));
        assertFalse(StackKeys.isBoxInstanceJson(JsonParser.parseString("{\"type\":\"wei\",\"key\":{}}")));
        assertFalse(StackKeys.isBoxInstanceJson(JsonParser.parseString("\"minecraft:stone\"")));
        assertFalse(StackKeys.isBoxInstanceJson(null));
    }
}
