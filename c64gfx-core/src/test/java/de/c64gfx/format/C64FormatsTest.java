package de.c64gfx.format;

import de.c64gfx.palette.Palettes;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic files with known bytes at the documented positions. */
class C64FormatsTest {

    private static int px(C64Picture p, int x, int y) { return p.frame1()[y * 320 + x]; }
    private static int px2(C64Picture p, int x, int y) { return p.frame2()[y * 320 + x]; }

    /** cell = character cell 0..999, line = 0..7 */
    private static void bitmap(byte[] d, int bitmapOffset, int cell, int line, int value) {
        d[bitmapOffset + cell * 8 + line] = (byte) value;
    }

    @Test
    void koala() throws IOException {
        byte[] d = new byte[10003];
        d[1] = 0x60;
        bitmap(d, 2, 0, 0, 0b00_01_10_11);
        d[0x1f42] = 0x12;         // screen RAM: high nibble for 01, low nibble for 10
        d[0x232a] = 3;            // colour RAM for 11
        d[0x2712] = 6;            // background for 00
        C64Picture p = C64Formats.read("x.kla", d);
        assertEquals("Koala Painter", p.format());
        assertFalse(p.interlaced());
        assertArrayEquals(new int[]{6, 6, 1, 1, 2, 2, 3, 3}, java.util.Arrays.copyOfRange(p.frame1(), 0, 8));
        assertEquals(6, px(p, 8, 0), "next cell is background");
    }

    @Test
    void artStudio() throws IOException {
        byte[] d = new byte[9009];
        bitmap(d, 2, 41, 3, 0b1000_0000);   // cell 41 = row 1, column 1
        d[0x1f42 + 41] = 0x21;
        C64Picture p = C64Formats.read("x.art", d);
        assertFalse(p.multicolor());
        assertEquals(2, px(p, 8, 11));
        assertEquals(1, px(p, 9, 11));
    }

    @Test
    void fliUsesOneScreenPerLineAndHidesTheBugColumns() throws IOException {
        byte[] d = new byte[17409];
        int cell = 3;                                    // first visible column
        for (int line = 0; line < 8; line++) {
            bitmap(d, 0x2402, cell, line, 0b01_01_01_01);
            d[0x402 + (line << 10) + cell] = (byte) (line << 4);   // screen n: high nibble n
        }
        bitmap(d, 0x2402, 0, 0, 0b11_11_11_11);          // inside the bug area: not shown
        d[2] = 5;
        C64Picture p = C64Formats.read("x.fli", d);
        for (int y = 0; y < 8; y++) assertEquals(y, px(p, 24, y), "line " + y);
        assertEquals(0, px(p, 0, 0), "FLI bug area shows the background");
    }

    @Test
    void fliGraphHasABackgroundPerLine() throws IOException {
        byte[] d = new byte[17474];
        for (int y = 0; y < 200; y++) d[2 + y] = (byte) (y % 16);
        C64Picture p = C64Formats.read("x.bml", d);
        assertEquals("FLI Graph", p.format());
        assertEquals(7, px(p, 100, 7));
        assertEquals(199 % 16, px(p, 100, 199));
        bitmap(d, 0x2502, 5, 2, 0b11_00_00_00);
        d[0x102 + 5] = 9;
        assertEquals(9, px(C64Formats.read("x.fli", d), 40, 2), "17474-byte .fli files are FLI Graph too");
    }

    @Test
    void afli() throws IOException {
        byte[] d = new byte[16385];
        bitmap(d, 0x2002, 10, 2, 0b0100_0000);
        d[2 + (2 << 10) + 10] = (byte) 0xA3;            // screen of line 2
        C64Picture p = C64Formats.read("x.afl", d);
        assertEquals(3, px(p, 80, 2));
        assertEquals(10, px(p, 81, 2));
        assertEquals(0, px(p, 0, 50), "bug area");
    }

    @Test
    void gunpaintHasTwoFramesAndBackgroundTable() throws IOException {
        byte[] d = new byte[33603];
        d[1] = 0x40;
        d[0x3f51 + 10] = 4;          // background line 10
        d[0x47ea] = 7;               // background line 177
        d[0x47fd] = 8;               // background lines 197..199
        bitmap(d, 0x2002, 3, 0, 0b10_00_00_00);
        d[2 + 3] = 0x0C;
        bitmap(d, 0x6402, 3, 0, 0b10_00_00_00);
        d[0x4402 + 3] = 0x0E;
        C64Picture p = C64Formats.read("x.gun", d);
        assertTrue(p.interlaced());
        assertEquals(4, px(p, 100, 10));
        assertEquals(7, px(p, 100, 177));
        assertEquals(8, px(p, 100, 199));
        assertEquals(12, px(p, 24, 0));
        assertEquals(12, px(p, 25, 0));
        assertEquals(0, px2(p, 24, 0), "frame 2 is shifted one pixel to the right");
        assertEquals(14, px2(p, 25, 0));
        assertEquals(14, px2(p, 26, 0));
        int[] rgb = p.render(Palettes.PEPTO);
        assertTrue(Palettes.PEPTO.rgb(12) != rgb[24], "frames are mixed");
    }

    @Test
    void funpaintPackedEqualsUnpacked() throws IOException {
        byte[] raw = new byte[33694];
        byte[] sig = "FUNPAINT (MT) ".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(sig, 0, raw, 2, sig.length);
        bitmap(raw, 0x2012, 3, 0, 0b01_00_00_00);
        raw[0x12 + 3] = (byte) 0xB0;
        for (int i = 0x4012; i < 0x4012 + 1000; i++) raw[i] = 5;   // a long run for the compression
        C64Picture plain = C64Formats.read("x.fun", raw);
        assertEquals(11, px(plain, 24, 0));

        byte[] packed = packFunpaint(raw);
        assertTrue(packed.length < raw.length);
        C64Picture unpacked = C64Formats.read("x.fun", packed);
        assertArrayEquals(plain.frame1(), unpacked.frame1());
        assertArrayEquals(plain.frame2(), unpacked.frame2());
    }

    @Test
    void drazlacePacked() throws IOException {
        byte[] raw = new byte[18242];
        bitmap(raw, 0x802, 0, 0, 0b11_00_00_00);
        bitmap(raw, 0x2802, 0, 0, 0b11_00_00_00);
        raw[2] = 13;                  // colour RAM
        raw[0x2742] = 1;              // background
        raw[0x2744] = 1;              // frame 2 shifted
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0); o.write(0x58);
        o.writeBytes("DRAZLACE! 1.0".getBytes(StandardCharsets.US_ASCII));
        o.write(0xFE);                // escape byte
        pack(raw, 2, raw.length, 0xFE, o);
        C64Picture p = C64Formats.read("x.drl", o.toByteArray());
        assertEquals("Drazlace", p.format());
        assertEquals(13, px(p, 0, 0));
        assertEquals(1, px2(p, 0, 0));
        assertEquals(13, px2(p, 1, 0));
        assertEquals(1, px(p, 2, 0));
    }

    @Test
    void unpackHandlesRunsAndZeroCounts() throws IOException {
        byte[] d = {9, 9, 1, 0xFE - 256, 4, 7, 0xFE - 256, 0, 3, 2};
        byte[] u = C64Formats.unpack(d, 2, 0xFE, 0, 6);
        assertArrayEquals(new byte[]{1, 7, 7, 7, 7, 2}, u);
    }

    @Test
    void wrongSizesAreRejected() {
        assertThrows(IOException.class, () -> C64Formats.read("x.fli", new byte[1234]));
        assertThrows(IOException.class, () -> C64Formats.read("x.png", new byte[10003]));
        assertTrue(C64Formats.isC64File("PIC.GUN"));
        assertFalse(C64Formats.isC64File("pic.png"));
    }

    // a simple packer in the Drazpaint/Funpaint style, only for the tests
    private static byte[] packFunpaint(byte[] raw) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(raw, 0, 16);
        o.write(1);                   // packed flag
        o.write(0xFE);                // escape byte
        pack(raw, 18, raw.length, 0xFE, o);
        return o.toByteArray();
    }

    private static void pack(byte[] raw, int from, int to, int escape, ByteArrayOutputStream o) {
        int i = from;
        while (i < to) {
            int b = raw[i] & 0xFF, n = 1;
            while (i + n < to && n < 255 && (raw[i + n] & 0xFF) == b) n++;
            if (n > 3 || b == escape) { o.write(escape); o.write(n); o.write(b); }
            else for (int k = 0; k < n; k++) o.write(b);
            i += n;
        }
    }
}
