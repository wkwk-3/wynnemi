package io.github.wkwk.wei.util;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;

/** 名前・ID の整形。 */
public final class Names {
    /**
     * Wynncraft のフォントのアイコン（私用領域）。補助私用領域（例: U+100002）も含む。
     * 正規表現はコードポイント単位で照合するため、サロゲートの範囲指定では消せない。
     */
    private static final Pattern PRIVATE_USE = Pattern.compile("\\p{Co}");
    public static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ID_UNSAFE = Pattern.compile("[^a-z0-9/._-]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    private Names() {}

    public static String strip(String s) {
        String stripped = ChatFormatting.stripFormatting(s);
        if (stripped == null) return "";
        return WHITESPACE.matcher(PRIVATE_USE.matcher(stripped).replaceAll("")).replaceAll(" ").trim();
    }

    /** 表記揺れ（大小文字・空白・記号・色コード）を吸収した照合キー */
    public static String normalize(String name) {
        return NON_ALNUM.matcher(strip(name).toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** 英数字だけの小文字（"Hurricane's Eye" → "hurricaneseye"） */
    public static String compact(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** MASTERY_TOME → Mastery Tome */
    public static String titleCase(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String word : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    public static String idPath(String s) {
        return ID_UNSAFE.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("_");
    }
}
