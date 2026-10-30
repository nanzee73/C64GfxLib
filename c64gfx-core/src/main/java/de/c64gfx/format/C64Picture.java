package de.c64gfx.format;

import de.c64gfx.palette.Palette;

/**
 * A C64 picture read from a C64 file, as 320×200 palette indices (one per hires pixel; multicolor pixels are
 * stored twice). Interlace pictures (Gunpaint, Funpaint, Drazlace) have a second frame, which is shown alternately
 * with the first one on the C64 – {@link #render(Palette)} mixes both like the eye does.
 *
 * @param format    name of the file format, e.g. "FLI Graph"
 * @param frame1    320×200 palette indices 0..15
 * @param frame2    second frame of an interlace picture, otherwise {@code null}
 * @param multicolor true for multicolor pictures (2×1 pixels), false for hires
 */
public record C64Picture(String format, int[] frame1, int[] frame2, boolean multicolor) {
    public static final int WIDTH = 320, HEIGHT = 200;

    public boolean interlaced() { return frame2 != null; }

    /** 320×200 pixels as 0xRRGGBB; interlace frames are averaged. */
    public int[] render(Palette p) {
        int[] out = new int[WIDTH * HEIGHT];
        for (int i = 0; i < out.length; i++) {
            int a = p.rgb(frame1[i]);
            if (frame2 == null) {
                out[i] = a;
                continue;
            }
            int b = p.rgb(frame2[i]);
            int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) >> 1;
            int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) >> 1;
            int bl = ((a & 0xFF) + (b & 0xFF)) >> 1;
            out[i] = (r << 16) | (g << 8) | bl;
        }
        return out;
    }
}
