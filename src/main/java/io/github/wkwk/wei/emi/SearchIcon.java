package io.github.wkwk.wei.emi;

import net.minecraft.client.gui.GuiGraphics;

/** 条件検索ボタンの虫眼鏡。12x12 のドット絵を塗りで描く。 */
public final class SearchIcon {
    private static final String[] PIXELS = {
            "...####.....",
            "..#....#....",
            ".#......#...",
            ".#......#...",
            ".#......#...",
            ".#......#...",
            "..#....#....",
            "...####.#...",
            ".........#..",
            "..........#.",
            "...........#",
            "............",
    };
    private static final int COLOR = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF3F3F3F;

    private SearchIcon() {}

    public static void draw(GuiGraphics g, int x, int y) {
        for (int pass = 0; pass < 2; pass++) {
            int off = pass == 0 ? 1 : 0;
            int color = pass == 0 ? SHADOW : COLOR;
            for (int row = 0; row < PIXELS.length; row++) {
                String line = PIXELS[row];
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '#') continue;
                    // 柄は 2px
                    int w = row >= 7 && col >= 8 ? 2 : 1;
                    g.fill(x + col + off, y + row + off, x + col + off + w, y + row + off + 1, color);
                }
            }
        }
    }
}
