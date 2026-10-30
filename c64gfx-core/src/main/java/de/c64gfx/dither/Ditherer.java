package de.c64gfx.dither;

import de.c64gfx.color.ColorMatcher;
import de.c64gfx.palette.Palette;

/**
 * Error diffusion or ordered dithering onto palette indices.
 * <p>
 * Optionally every pixel may only use the colours allowed in its <em>colour cell</em>
 * ({@code allowed[(y / cellH) * (w / cellW) + x / cellW]}): Koala uses 4×8 cells with background + 3 colours,
 * hires 8×8 cells with 2 colours, FLI 4×1 (multicolor) or 8×1 (hires) cells because the colours change every line.
 * <p>
 * Without bleeding the diffused error never leaves its <em>bleed cell</em>, which is normally the 4×8 or 8×8
 * character cell even when the colour cells are only one line high (FLI).
 */
public final class Ditherer {

    /**
     * @param strength   0..100 %
     * @param serpentine scan every second line from right to left
     * @param bleeding   with cells: let the diffused error cross cell borders
     */
    public record Settings(Dither dither, int strength, boolean serpentine, boolean bleeding) {
        public static final Settings NONE = new Settings(Dither.NONE, 80, false, true);

        public boolean active() { return dither != Dither.NONE && strength > 0; }
    }

    /** Cell layout for restricted colours. */
    public record Cells(int[][] allowed, int cellW, int cellH, int bleedW, int bleedH) {
        /** Colour cells that are also the bleed cells (Koala 4×8, hires 8×8). */
        public static Cells of(int[][] allowed, int cellW, int cellH) {
            return new Cells(allowed, cellW, cellH, cellW, cellH);
        }
    }

    private static final int[] ALL = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

    private Ditherer() {}

    private static float clamp(float v) { return v < 0 ? 0 : Math.min(255, v); }

    /**
     * @param rgb     pixels as float[w*h*3] (0..255)
     * @param allowed colours per cell (cells row by row, {@code w / cellW} per row), null = all 16 everywhere
     * @return palette index per pixel
     */
    public static int[] run(float[] rgb, int w, int h, ColorMatcher m, Palette pal, Settings s,
                            int[][] allowed, int cellW, int cellH) {
        return run(rgb, w, h, m, pal, s, allowed == null ? null : Cells.of(allowed, cellW, cellH), null);
    }

    /**
     * @param cells   colour restrictions, null = all 16 colours everywhere
     * @param seenOut optional float[w*h*3]: receives the colour each pixel wanted (target + diffused error),
     *                useful to refine the colour choice in a second pass
     * @return palette index per pixel
     */
    public static int[] run(float[] rgb, int w, int h, ColorMatcher m, Palette pal, Settings s,
                            Cells cells, float[] seenOut) {
        Dither d = s.dither();
        float strength = s.strength() / 100f;
        float[] err = new float[w * h * 3];
        int[] out = new int[w * h];
        int cols = cells != null ? w / cells.cellW() : 1;
        for (int y = 0; y < h; y++) {
            int dir = s.serpentine() && (y & 1) == 1 ? -1 : 1;
            for (int xi = 0; xi < w; xi++) {
                int x = dir > 0 ? xi : w - 1 - xi;
                int i = y * w + x;
                int[] al = cells == null ? ALL : cells.allowed()[(y / cells.cellH()) * cols + x / cells.cellW()];
                float r = clamp(rgb[i * 3] + err[i * 3]);
                float g = clamp(rgb[i * 3 + 1] + err[i * 3 + 1]);
                float b = clamp(rgb[i * 3 + 2] + err[i * 3 + 2]);
                float mr = r, mg = g, mb = b;
                if (d.isOrdered()) {
                    float t = d.threshold(x, y) * strength * 96f;
                    mr = clamp(r + t); mg = clamp(g + t); mb = clamp(b + t);
                }
                int k = al.length == 1 ? al[0] : m.nearest(mr, mg, mb, al, al.length);
                out[i] = k;
                if (seenOut != null) {
                    seenOut[i * 3] = r; seenOut[i * 3 + 1] = g; seenOut[i * 3 + 2] = b;
                }
                if (!d.isErrorDiffusion() || strength <= 0) continue;
                float er = (r - pal.r(k)) * strength, eg = (g - pal.g(k)) * strength, eb = (b - pal.b(k)) * strength;
                for (int[] kv : d.kernel) {
                    int nx = x + kv[0] * dir, ny = y + kv[1];
                    if (nx < 0 || nx >= w || ny >= h) continue;
                    if (cells != null && !s.bleeding()
                            && (nx / cells.bleedW() != x / cells.bleedW() || ny / cells.bleedH() != y / cells.bleedH())) {
                        continue;
                    }
                    float wt = (float) kv[2] / d.divisor;
                    int j = (ny * w + nx) * 3;
                    err[j] += er * wt;
                    err[j + 1] += eg * wt;
                    err[j + 2] += eb * wt;
                }
            }
        }
        return out;
    }
}
