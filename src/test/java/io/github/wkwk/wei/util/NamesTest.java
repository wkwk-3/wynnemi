package io.github.wkwk.wei.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Minecraft に触れない整形（compact・titleCase・idPath） */
class NamesTest {
    @Test
    void compactKeepsOnlyLowercaseLettersAndDigits() {
        assertEquals("hurricaneseye", Names.compact("Hurricane's Eye"));
        assertEquals("superfast", Names.compact("SUPER_FAST"));
        assertEquals("tier2", Names.compact("Tier 2"));
        assertEquals("", Names.compact(" -_ "));
    }

    @Test
    void titleCaseFromEnumName() {
        assertEquals("Mastery Tome", Names.titleCase("MASTERY_TOME"));
        assertEquals("Wand", Names.titleCase("WAND"));
        assertEquals("A B", Names.titleCase("_a__b_"));
    }

    @Test
    void idPathReplacesUnsafeCharacters() {
        assertEquals("decrepit_sewers", Names.idPath("Decrepit Sewers"));
        assertEquals("a/b.c-d_e", Names.idPath("A/B.C-D_E"));
        assertEquals("hurricane_s_eye", Names.idPath("Hurricane's Eye"));
    }

    @Test
    void nameKeyDropsFontGlyphsAndSpacingCharacters() {
        // ルーン名の前の字送り文字（U+CFFF0 など、未割り当て）と私用領域のアイコン
        assertEquals("az rune", Names.nameKey(new String(Character.toChars(0xCFFF0)) + new String(Character.toChars(0xCFFCF))
                + new String(Character.toChars(0xD0005)) + "Az Rune"));
        assertEquals("tol rune", Names.nameKey("\uE000Tol  Rune" + new String(Character.toChars(0x100002))));
        assertEquals("emerald pouch [tier 7]", Names.nameKey("§aEmerald Pouch [Tier 7]"));
        // 日本語はそのまま
        assertEquals("エメラルド", Names.nameKey("エメラルド"));
    }
}
