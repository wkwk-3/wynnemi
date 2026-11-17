package io.github.wkwk.wei.util;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;

/** 名前・ID の整形（正規表現は使い回す） */
public final class Names {
    /** Wynncraft のフォントのアイコン（私用領域の文字） */
    private static final Pattern PRIVATE_USE = Pattern.compile("[\\uE000-\\uF8FF]|[\\uDB80-\\uDBFF][\\uDC00-\\uDFFF]");
    public static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ID_UNSAFE = Pattern.compile("[^a-z0-9/._-]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    private Names() {}

    /** 色コードとアイコン文字を除き、空白をまとめる */
    public static String strip(String s) {
        String stripped = ChatFormatting.stripFormatting(s);
        if (stripped == null) return "";
        return WHITESPACE.matcher(PRIVATE_USE.matcher(stripped).replaceAll("")).replaceAll(" ").trim();
    }

    /** 表記揺れ（大文字小文字・空白・記号・色コード）をまとめるためのキー */
    public static String normalize(String name) {
        return NON_ALNUM.matcher(strip(name).toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** 英数字だけの小文字（"Hurricane's Eye" → "hurricaneseye"）。表記の違う名前を突き合わせるため */
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

    /** Identifier のパスに使えない文字を置き換える */
    public static String idPath(String s) {
        return ID_UNSAFE.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("_");
    }
}
