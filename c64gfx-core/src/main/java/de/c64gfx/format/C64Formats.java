package de.c64gfx.format;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Reads pictures of C64 paint programs: plain bitmaps (Koala, Art Studio, Advanced Art Studio, Drazpaint),
 * FLI (FLI, FLI Designer, FLI Graph, FLI Editor), AFLI and interlace (Gunpaint, Funpaint, Drazlace).
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
            "fli", "fd2", "bml", "flg", "fed", "afl", "gun", "ifl", "fun", "fp2");

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
