package de.c64gfx.format;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Reads pictures of C64 paint programs: plain bitmaps (Koala, Art Studio, Advanced Art Studio, Drazpaint),
 * FLI (FLI, FLI Designer, FLI Graph, FLI Editor), AFLI, interlace (Gunpaint, Funpaint, Drazlace) and the sprite
 * underlay modes NUFLI, MUFLI and MUIFLI (also compressed).
 * <p>
 * The format is chosen by file extension and size. All offsets below are positions in the file including the
 * two bytes of the load address. FLI pictures cannot show the leftmost three character columns (the "FLI bug");
 * these 24 pixels are drawn in the background colour.
 * <p>
 * File layouts as documented by RECOIL (https://recoil.sourceforge.net), this is an independent implementation.
 */
public final class C64Formats {
    private C64Formats() {}

    /** File extensions (lower case, without dot) of all readable formats. */
    public static final List<String> EXTENSIONS = List.of(
            "kla", "koa", "gig", "art", "aas", "hpi", "ocp", "mpic", "drz", "drp", "drl", "dlp",
            "fli", "fd2", "bml", "flg", "fed", "afl", "gun", "ifl", "fun", "fp2",
            "nuf", "nup", "muf", "mup", "mui");

    /** Number of leftmost character columns an FLI picture cannot use. */
    public static final int FLI_BUG_COLUMNS = 3;

    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** True if the file name has an extension of a C64 format this class can read. */
    public static boolean isC64File(String fileName) {
        return EXTENSIONS.contains(extension(fileName));
    }

    /**
     * Reads a C64 picture.
     *
     * @param fileName used for the extension only
     * @param d        the file bytes
     * @throws IOException if the extension is unknown or the content does not fit the format
     */
    public static C64Picture read(String fileName, byte[] d) throws IOException {
        int n = d.length;
        switch (extension(fileName)) {
            case "kla", "koa", "gig" -> {
                if (n == 10001) return multicolor("Koala Painter", d, 0, 0x1f40, 0x2328, d[0x2710]);
                if (n >= 10003 && n <= 10010) return multicolor("Koala Painter", d, 2, 0x1f42, 0x232a, d[0x2712]);
            }
            case "art", "aas", "hpi" -> {
                if (n == 9002 || n == 9003 || n == 9009) return hires("Art Studio", d, 2, 0x1f42, false);
            }
            case "ocp", "mpic" -> {
                if (n == 10018) return multicolor("Advanced Art Studio", d, 2, 0x1f42, 0x233a, d[0x232b]);
            }
            case "drz", "drp" -> {
                byte[] u = unpackDrazPaint(d, "DRAZPAINT 1.4", 10051);
                return multicolor("Drazpaint", u, 0x802, 0x402, 2, u[0x2742]);
            }
            case "drl", "dlp" -> {
                byte[] u = unpackDrazPaint(d, "DRAZLACE! 1.0", 18242);
                int shift = u[0x2744] & 0xFF;
                if (shift > 1) break;
                int bg = u[0x2742];
                int[] f1 = new int[C64Picture.WIDTH * C64Picture.HEIGHT], f2 = new int[f1.length];
                multicolorFrame(u, 0x802, 0x402, 2, constant(bg), false, 0, f1);
                multicolorFrame(u, 0x2802, 0x402, 2, constant(bg), false, shift, f2);
                return new C64Picture("Drazlace", f1, f2, true);
            }
            case "fli", "fd2", "bml", "flg", "fed" -> {
                if (n == 17218 && loadAddress(d) == 0x3C00 && extension(fileName).equals("fli"))
                    return flipperJ("FLI (FlipperJ)", d, true, false);
                if (n == 17409 || n == 17218)                 // FLI Designer / Blackmail FLI
                    return fli("FLI", d, 0x2402, 0x402, 2, constant(0));
                if (n == 17474 || n == 17665 || n == 17666) { // FLI Graph: one background colour per line
                    int[] bg = new int[200];
                    int bgOffset = extension(fileName).equals("fed") ? 8 : 2;
                    for (int y = 0; y < 200; y++) bg[y] = d[bgOffset + y] & 15;
                    return fli(extension(fileName).equals("fed") ? "FLI Editor" : "FLI Graph", d, 0x2502, 0x502, 0x102, bg);
                }
                if (n == 17410)                               // Flimatic, uncompressed
                    return fli("FLI", d, 0x2402, 0x402, 2, constant(d[0x4381]));
            }
            case "afl" -> {
                if (n == 17218 && loadAddress(d) == 0x3C00) return flipperJ("AFLI (FlipperJ)", d, false, false);
                if (n == 16385) return hires("AFLI", d, 0x2002, 2, true);
            }
            case "gun", "ifl" -> {
                if (n == 33602 && loadAddress(d) == 0x3C00) return flipperJ("IFLI (FlipperJ)", d, true, true);
                if (n == 33602 || n == 33603) {
                    int[] bg = new int[200];
                    for (int y = 0; y < 200; y++)
                        bg[y] = d[y < 177 ? 0x3f51 + y : y < 197 ? 0x47ea - 177 + y : 0x47fd] & 15;
                    int[] f1 = new int[C64Picture.WIDTH * C64Picture.HEIGHT], f2 = new int[f1.length];
                    multicolorFrame(d, 0x2002, 2, 0x4002, bg, true, 0, f1);
                    multicolorFrame(d, 0x6402, 0x4402, 0x4002, bg, true, 1, f2);
                    return new C64Picture("Gunpaint", f1, f2, true);
                }
            }
            case "fun", "fp2" -> {
                byte[] u = unpackFunpaint(d);
                int[] f1 = new int[C64Picture.WIDTH * C64Picture.HEIGHT], f2 = new int[f1.length];
                multicolorFrame(u, 0x2012, 0x12, 0x4012, constant(0), true, 0, f1);
                multicolorFrame(u, 0x63fa, 0x43fa, 0x4012, constant(0), true, 1, f2);
                return new C64Picture("Funpaint", f1, f2, true);
            }
            case "nuf" -> {
                if (n == 23042) return nufli(d);
            }
            case "nup" -> {
                if (n >= 7 && (d[2] & 0xFF) == 0xFD) return nufli(unpackBackwards(d, d[3] & 0xFF, 23042));
            }
            case "muf" -> {
                if (n == 21826) return mufli("MUFLI Editor", d, null);
            }
            case "mup" -> {
                if (n >= 6) return mufli("MUFLI Editor", unpackBackwards(d, d[2] & 0xFF, 21826), null);
            }
            case "mui" -> {
                if (n == 44034) {
                    byte[] second = new byte[21826];
                    System.arraycopy(d, 0x8002, second, 2, 0x2c00);
                    System.arraycopy(d, 0x5602, second, 0x2c02, 0x2900);
                    return mufli("MUIFLI Editor", d, second);
                }
            }
            default -> throw new IOException(fileName + ": unknown C64 file type.");
        }
        throw new IOException(fileName + ": " + n + " bytes do not fit the expected C64 file format.");
    }

    // ------------------------------------------------------------------------------------------------------------

    private static int[] constant(int colour) {
        int[] bg = new int[200];
        java.util.Arrays.fill(bg, colour & 15);
        return bg;
    }

    private static C64Picture multicolor(String name, byte[] d, int bitmap, int screen, int colour, int background) {
        int[] f = new int[C64Picture.WIDTH * C64Picture.HEIGHT];
        multicolorFrame(d, bitmap, screen, colour, constant(background), false, 0, f);
        return new C64Picture(name, f, null, true);
    }

    private static C64Picture fli(String name, byte[] d, int bitmap, int screens, int colour, int[] background) {
        int[] f = new int[C64Picture.WIDTH * C64Picture.HEIGHT];
        multicolorFrame(d, bitmap, screens, colour, background, true, 0, f);
        return new C64Picture(name, f, null, true);
    }

    /**
     * Draws a multicolor bitmap.
     *
     * @param fli   true: one screen RAM per pixel line inside a character row (8 screens 1024 bytes apart) and
     *              the leftmost {@link #FLI_BUG_COLUMNS} columns show the background
     * @param shift the frame is shifted this many hires pixels to the right (interlace)
     */
    static void multicolorFrame(byte[] d, int bitmap, int screen, int colour, int[] background, boolean fli,
                                int shift, int[] out) {
        multicolorFrame(d, bitmap, screen, colour, background, fli, false, shift, out);
    }

    /**
     * @param flipperJ FlipperJ's displayer: picture line 0 is a normal bad line (no FLI bug), lines 197..199 repeat
     *                 the screen of line 196, and the bug cells show $FF screen data and light grey colour RAM
     */
    static void multicolorFrame(byte[] d, int bitmap, int screen, int colour, int[] background, boolean fli,
                                boolean flipperJ, int shift, int[] out) {
        for (int y = 0; y < 200; y++) {
            int bg = background[y];
            int screenLine = screen + (fli ? ((flipperJ ? Math.min(y, 196) : y) & 7) << 10 : 0);
            boolean bugLine = fli && (!flipperJ || y >= 1);
            for (int x = 0; x < 320; x++) {
                int sx = x - shift;
                int c;
                if (sx < 0 || (bugLine && !flipperJ && sx < FLI_BUG_COLUMNS * 8)) c = bg;
                else {
                    int cell = (y >> 3) * 40 + (sx >> 3);
                    boolean bug = bugLine && flipperJ && sx < FLI_BUG_COLUMNS * 8;
                    int scr = bug ? 0xFF : d[screenLine + cell] & 0xFF;
                    int pair = (d[bitmap + cell * 8 + (y & 7)] & 0xFF) >> (6 - (sx & 6)) & 3;
                    c = switch (pair) {
                        case 0 -> bg;
                        case 1 -> scr >> 4;
                        case 2 -> scr & 15;
                        default -> bug ? 15 : d[colour + cell] & 15;
                    };
                }
                out[y * 320 + x] = c;
            }
        }
    }

    private static int loadAddress(byte[] d) {
        return d.length < 2 ? -1 : (d[0] & 0xFF) | (d[1] & 0xFF) << 8;
    }

    /**
     * FlipperJ's raw file, load address $3C00: colour RAM $3C00, background $3FF0 (second frame $3FF1),
     * 8 screens $4000, bitmap $6000; IFLI frame B: screens $8000, bitmap $A000.
     */
    private static C64Picture flipperJ(String name, byte[] d, boolean multicolor, boolean interlaced) {
        int[] f1 = new int[C64Picture.WIDTH * C64Picture.HEIGHT];
        if (!multicolor) {
            for (int y = 0; y < 200; y++) {
                int screenLine = 0x402 + ((Math.min(y, 196) & 7) << 10);
                for (int x = 0; x < 320; x++) {
                    int cell = (y >> 3) * 40 + (x >> 3);
                    int v = y >= 1 && x < FLI_BUG_COLUMNS * 8 ? 0xFF : d[screenLine + cell] & 0xFF;
                    int bit = (d[0x2402 + cell * 8 + (y & 7)] & 0xFF) >> (7 - (x & 7)) & 1;
                    f1[y * 320 + x] = bit == 0 ? v & 15 : v >> 4;
                }
            }
            return new C64Picture(name, f1, null, false);
        }
        multicolorFrame(d, 0x2402, 0x402, 2, constant(d[0x3F2]), true, true, 0, f1);
        if (!interlaced) return new C64Picture(name, f1, null, true);
        int[] f2 = new int[f1.length];
        multicolorFrame(d, 0x6402, 0x4402, 2, constant(d[0x3F3]), true, true, 1, f2);
        return new C64Picture(name, f1, f2, true);
    }

    /** Hires bitmap: set bits use the screen RAM high nibble, cleared bits the low nibble. */
    private static C64Picture hires(String name, byte[] d, int bitmap, int screen, boolean afli) {
        int[] f = new int[C64Picture.WIDTH * C64Picture.HEIGHT];
        for (int y = 0; y < 200; y++) {
            int screenLine = screen + (afli ? (y & 7) << 10 : 0);
            for (int x = 0; x < 320; x++) {
                int cell = (y >> 3) * 40 + (x >> 3);
                int v = d[screenLine + cell] & 0xFF;
                int c;
                if (afli && x < FLI_BUG_COLUMNS * 8) c = 0;     // AFLI has no background colour; the bug area is black
                else {
                    int bit = (d[bitmap + cell * 8 + (y & 7)] & 0xFF) >> (7 - (x & 7)) & 1;
                    c = bit == 0 ? v & 15 : v >> 4;
                }
                f[y * 320 + x] = c;
            }
        }
        return new C64Picture(name, f, null, false);
    }

    // ------------------------------------------------------------------------------------------------------------
    // NUFLI / MUFLI / MUIFLI: an FLI bitmap (new screen RAM every second line) over a layer of sprites that fills the
    // cleared bitmap pixels with more colours. Picture line y uses screen 0..7 as given by nufScreen; sprite data
    // pointers and colours change every second line.

    /** File offset of the screen RAM used by picture line y. */
    private static int nufScreen(int y) {
        if (y < 128) return 0x2002 + ((((~y) >> 1) & 7) << 10);
        return 2 + (((((y >> 1) + (y >= 168 ? 1 : 0)) & 3)) << 10);
    }

    /** Offset of the colour table of sprite 0..5 (one byte per line pair). */
    private static int spriteTable(int sprite) {
        return ((sprite & 6) << 9) + ((sprite & 1) << 7);
    }

    /** Byte of a sprite line: which of the 21 sprite lines a picture line shows depends on the sprite row. */
    private static int spriteByte(byte[] d, int block, int y, int column) throws IOException {
        if (block < 0 || (block << 6) + 2 + 63 >= d.length) throw new IOException("Broken sprite pointer.");
        int line = (((y + 47) >> 1) * 3 + column + (y < 123 ? 0 : y < 165 ? 1 : 2)) & 63;
        return d[2 + (block << 6) + line] & 0xFF;
    }

    /** NUFLI Editor: hires 320×200, 6 sprites under the bitmap plus a multicolor sprite under the FLI bug area. */
    private static C64Picture nufli(byte[] d) throws IOException {
        int[] f = new int[C64Picture.WIDTH * C64Picture.HEIGHT];
        int[] sc = new int[10];
        sc[0] = d[0x1ff9];
        for (int i = 0; i < 6; i++) sc[1 + i] = d[0x402 + spriteTable(i)];
        sc[7] = d[0x1ff3];
        sc[8] = d[0x1ff8];
        sc[9] = d[0x1ff2];
        for (int y = 0; y < 200; y++) {
            if (y > 0) {                                  // colour changes written by the displayer every line pair
                for (int i = 0; i < 6; i++) {
                    int b = d[0x402 + spriteTable(i) + ((y + 1) >> 1)] & 0xFF;
                    boolean thisLine = (y & 1) == (i == 0 ? 1 : 0);
                    switch (b >> 4) {
                        case 0 -> { if ((y & 1) != 0) sc[1 + i] = b; }
                        case 5 -> { if (thisLine) sc[7] = b; }
                        case 6 -> { if (thisLine) sc[9] = b; }
                        case 7 -> { if (thisLine) sc[0] = b; }
                        case 14 -> { if (thisLine) sc[8] = b; }
                        default -> { }
                    }
                }
            }
            int screen = nufScreen(y);
            for (int x = 0; x < 320; x++) {
                int column = x >> 3;
                int offset = (y & ~7) * 5 + column;
                int c = column >= 3 || (y & 6) == 0 ? d[screen + offset] & 0xFF : 0xFF;
                int bit = (d[(y < 128 ? 0x4002 : 2) + (offset << 3) + (y & 7)] & 0xFF) >> (7 - (x & 7)) & 1;
                if (bit != 0) c >>= 4;
                else if (column < 39) {
                    int sprite = (3 + column) / 6;
                    int block = 128 - ((y & 128) << 1) + (d[screen + 0x3f8 + sprite] & 0xFF);
                    int sx = column < 3 ? x : ((x - 24) >> 1) % 24;
                    int b = spriteByte(d, block, y, sx >> 3);
                    if ((b >> (7 - (sx & 7)) & 1) != 0) c = sc[sprite];
                    else if (column < 3) {
                        block = 128 - ((y & 128) << 1) + (d[screen + 0x3ff] & 0xFF);
                        int pair = spriteByte(d, block, y, column) >> (6 - (x & 6)) & 3;
                        if (pair != 0) c = sc[6 + pair];
                    }
                }
                f[y * 320 + x] = c & 15;
            }
        }
        return new C64Picture("NUFLI Editor", f, null, false);
    }

    /** MUFLI / MUIFLI Editor: hires bitmap over multicolor or hires sprites, 296 pixels wide (FLI bug area black). */
    private static C64Picture mufli(String name, byte[] d, byte[] second) throws IOException {
        int[] f1 = new int[C64Picture.WIDTH * C64Picture.HEIGHT];
        mufliFrame(d, f1);
        if (second == null) return new C64Picture(name, f1, null, false);
        int[] f2 = new int[f1.length];
        mufliFrame(second, f2);
        return new C64Picture(name, f1, f2, false);
    }

    private static void mufliFrame(byte[] d, int[] out) throws IOException {
        for (int y = 0; y < 200; y++) {
            int screen = nufScreen(y) - 0x100;
            int pair = (y + 1) >> 1;
            for (int x = 0; x < 320 - FLI_BUG_COLUMNS * 8; x++) {
                int column = x >> 3;
                int offset = (y & ~7) * 5 + FLI_BUG_COLUMNS + column;
                int c = d[screen + offset] & 0xFF;
                int bit = (d[0x3f02 - ((y & 128) << 7) + (offset << 3) + (y & 7)] & 0xFF) >> (7 - (x & 7)) & 1;
                if (bit != 0) c >>= 4;
                else if (column < 36) {
                    int sprite = column / 6;
                    int block = 124 - ((y & 128) << 1) + (d[screen + 0x3f9 + sprite] & 0xFF);
                    if (block >= 340) throw new IOException("Broken sprite pointer.");
                    int b = spriteByte(d, block, y, (column >> 1) % 3);
                    int own = d[0x302 + spriteTable(sprite) + pair] & 0xFF;
                    if (((d[0x1202 + pair] & 0xFF) >> sprite & 2) != 0) {   // multicolor sprite in this line pair
                        switch (b >> (((~x) >> 1) & 6) & 3) {
                            case 1 -> c = d[0x1ef3];
                            case 2 -> c = own;
                            case 3 -> c = d[0x1ef2];
                            default -> { }
                        }
                    } else if ((b >> (((~x) >> 1) & 7) & 1) != 0) c = own;
                }
                out[y * 320 + FLI_BUG_COLUMNS * 8 + x] = c & 15;
            }
        }
    }

    /**
     * Compression of the NUFLI and MUFLI editors: read from the end of the file towards the start and written from
     * the end of the picture backwards; a byte equal to the escape byte is followed by a count (0 = 256) and a value.
     */
    static byte[] unpackBackwards(byte[] d, int escape, int size) throws IOException {
        byte[] out = new byte[size];
        int pos = d.length, o = size - 1;
        while (o >= 2) {
            if (pos <= 2) throw new IOException("Compressed C64 file ends too early.");
            int b = d[--pos] & 0xFF;
            int count = 1;
            if (b == escape) {
                if (pos <= 3) throw new IOException("Compressed C64 file ends too early.");
                count = d[--pos] & 0xFF;
                if (count == 0) count = 256;
                b = d[--pos] & 0xFF;
            }
            for (int i = 0; i < count && o >= 2; i++) out[o--] = (byte) b;
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Run length compression of Drazpaint, Drazlace and Funpaint: a byte equal to the escape byte is followed by a
    // count and the value to repeat; every other byte stands for itself.

    private static byte[] unpackDrazPaint(byte[] d, String signature, int size) throws IOException {
        if (d.length > 16 && startsWith(d, 2, signature))
            return unpack(d, 16, d[15] & 0xFF, 2, size);
        if (d.length != size) throw new IOException("Not a " + signature.split(" ")[0] + " file: " + d.length + " bytes.");
        return d;
    }

    private static byte[] unpackFunpaint(byte[] d) throws IOException {
        if (d.length < 18 || !startsWith(d, 2, "FUNPAINT (MT) ")) throw new IOException("Not a Funpaint file.");
        if (d[16] != 0) return unpack(d, 18, d[17] & 0xFF, 18, 33694);
        if (d.length != 33694) throw new IOException("Funpaint file has " + d.length + " bytes, expected 33694.");
        return d;
    }

    static byte[] unpack(byte[] d, int pos, int escape, int start, int size) throws IOException {
        byte[] out = new byte[size];
        int o = start;
        while (o < size) {
            if (pos >= d.length) throw new IOException("Compressed C64 file ends too early.");
            int b = d[pos++] & 0xFF;
            int count = 1;
            if (b == escape) {
                if (pos + 1 >= d.length) throw new IOException("Compressed C64 file ends too early.");
                count = d[pos++] & 0xFF;
                b = d[pos++] & 0xFF;            // a count of 0 writes nothing
            }
            for (int i = 0; i < count && o < size; i++) out[o++] = (byte) b;
        }
        return out;
    }

    private static boolean startsWith(byte[] d, int pos, String s) {
        if (d.length < pos + s.length()) return false;
        for (int i = 0; i < s.length(); i++)
            if ((d[pos + i] & 0xFF) != s.charAt(i)) return false;
        return true;
    }
}
