package de.c64gfx;

import de.c64gfx.color.ColorMatcher;
import de.c64gfx.dither.Dither;
import de.c64gfx.dither.Ditherer;
import de.c64gfx.format.HiresImage;
import de.c64gfx.format.KoalaImage;
import de.c64gfx.palette.Palette;
import de.c64gfx.palette.Palettes;
import de.c64gfx.prg.Asm6502;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    private static final Palette PAL = Palettes.PEPTO;

    @Test
    void builtInPalettesHaveSixteenColours() {
        for (Palette p : Palettes.builtIns()) assertEquals(16, p.toArray().length, p.name());
    }

    @Test
    void exactPaletteColoursMatchThemselves() {
        for (ColorMatcher.Space sp : ColorMatcher.Space.values()) {
            ColorMatcher m = new ColorMatcher(PAL, sp, 50, 50, false);
            int[] all = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
            for (int i = 0; i < 16; i++) assertEquals(i, m.nearest(PAL.r(i), PAL.g(i), PAL.b(i), all, 16), sp + " " + i);
        }
    }

    @Test
    void ditheringMixesColoursAndKeepsCellRestrictions() {
        int w = 32, h = 16;
        float[] grey = new float[w * h * 3];
        java.util.Arrays.fill(grey, 128f);
        ColorMatcher m = new ColorMatcher(PAL, ColorMatcher.Space.RGB, 50, 50, false);
        int[] free = Ditherer.run(grey, w, h, m, PAL, new Ditherer.Settings(Dither.FLOYD_STEINBERG, 100, false, true),
                null, 0, 0);
        Set<Integer> used = new HashSet<>();
        for (int v : free) used.add(v);
        assertTrue(used.size() >= 2, "dithering should mix colours: " + used);

        int[][] allowed = new int[(w / 8) * (h / 8)][];
        for (int i = 0; i < allowed.length; i++) allowed[i] = new int[]{0, 1};
        int[] cells = Ditherer.run(grey, w, h, m, PAL, new Ditherer.Settings(Dither.ATKINSON, 80, true, false),
                allowed, 8, 8);
        for (int v : cells) assertTrue(v == 0 || v == 1);
    }

    @Test
    void bayerMatricesArePermutations() {
        for (int n : new int[]{2, 4, 8}) {
            Set<Integer> seen = new HashSet<>();
            for (int[] row : Dither.bayer(n)) for (int v : row) seen.add(v);
            assertEquals(n * n, seen.size());
        }
    }

    @Test
    void koalaRoundTrip() throws Exception {
        Random r = new Random(1);
        int bg = 6;
        int[] idx = new int[160 * 200];
        for (int cell = 0; cell < 1000; cell++) {
            int[] cols = {bg, r.nextInt(16), r.nextInt(16), r.nextInt(16)};
            int cx = (cell % 40) * 4, cy = (cell / 40) * 8;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 4; x++) idx[(cy + y) * 160 + cx + x] = cols[r.nextInt(4)];
        }
        KoalaImage k = KoalaImage.encode(idx, bg);
        assertArrayEquals(idx, k.indices());
        byte[] file = k.toBytes();
        assertEquals(10003, file.length);
        assertEquals(k, KoalaImage.read(file));
        int[] rgb = k.render(PAL);
        assertEquals(320 * 200, rgb.length);
        assertEquals(PAL.rgb(idx[0]), rgb[0]);
        assertEquals(PAL.rgb(idx[0]), rgb[1]);
    }

    @Test
    void hiresRoundTrip() throws Exception {
        Random r = new Random(2);
        int[] idx = new int[320 * 200];
        for (int cell = 0; cell < 1000; cell++) {
            int a = r.nextInt(16), b = r.nextInt(16);
            int cx = (cell % 40) * 8, cy = (cell / 40) * 8;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) idx[(cy + y) * 320 + cx + x] = r.nextBoolean() ? a : b;
        }
        HiresImage hi = HiresImage.encode(idx);
        byte[] file = hi.toBytes();
        assertEquals(9009, file.length);
        assertArrayEquals(idx, HiresImage.read(file).indices());
    }

    @Test
    void assemblerResolvesLabelsAndBranches() {
        Asm6502 a = new Asm6502(0x1000);
        a.label("loop").inx().bne("loop").jmp("end").nop().label("end").rts();
        byte[] b = a.assemble();
        assertArrayEquals(new byte[]{(byte) 0xE8, (byte) 0xD0, (byte) 0xFD, 0x4C, 0x07, 0x10, (byte) 0xEA, 0x60}, b);
    }
}
