package de.c64gfx.swing;

import de.c64gfx.format.C64Formats;
import de.c64gfx.format.C64Picture;
import de.c64gfx.palette.Palette;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Conversions between {@link BufferedImage} and the plain arrays used by c64gfx-core. */
public final class Images {
    private Images() {}

    /** Pixels as float[w*h*3] (0..255). */
    public static float[] toFloat(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        float[] f = new float[w * h * 3];
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 3, c = row[x];
                f[i] = (c >> 16) & 0xFF;
                f[i + 1] = (c >> 8) & 0xFF;
                f[i + 2] = c & 0xFF;
            }
        }
        return f;
    }

    /**
     * Reads a picture: C64 files (see {@link C64Formats}) are drawn with the given palette, everything else is read
     * by ImageIO (including installed plugins). The result is always TYPE_INT_RGB.
     */
    public static BufferedImage read(File file, Palette c64Palette) throws IOException {
        if (C64Formats.isC64File(file.getName()))
            return fromRgb(C64Formats.read(file.getName(), Files.readAllBytes(file.toPath())).render(c64Palette),
                    C64Picture.WIDTH, C64Picture.HEIGHT);
        BufferedImage img = ImageIO.read(file);
        if (img == null) throw new IOException(file.getName() + ": no readable picture.");
        return toRgb(img);
    }

    /** Picture from RGB pixels (0xRRGGBB). */
    public static BufferedImage fromRgb(int[] rgb, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, rgb, 0, w);
        return img;
    }

    /** Picture from palette indices. */
    public static BufferedImage fromIndices(int[] idx, int w, int h, Palette p) {
        int[] rgb = new int[idx.length];
        for (int i = 0; i < idx.length; i++) rgb[i] = p.rgb(idx[i]);
        return fromRgb(rgb, w, h);
    }

    /** Copy in TYPE_INT_RGB; transparent areas become white. */
    public static BufferedImage toRgb(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, Color.WHITE, null);
        g.dispose();
        return out;
    }
}
