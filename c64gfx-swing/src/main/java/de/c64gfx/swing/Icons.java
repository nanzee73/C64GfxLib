package de.c64gfx.swing;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Loads the application icon in all bundled sizes (see {@link AppConfig#iconPrefix()}). */
public final class Icons {
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256, 512};
    private static List<Image> cache;

    private Icons() {}

    public static synchronized List<Image> appIcons() {
        if (cache != null) return cache;
        List<Image> list = new ArrayList<>();
        String prefix = AppConfig.current().iconPrefix();
        if (prefix == null) return cache = list;
        for (int s : SIZES) {
            try (InputStream in = resource(prefix + s + ".png")) {
                if (in != null) list.add(ImageIO.read(in));
            } catch (Exception ignored) {
                // a missing size is not fatal
            }
        }
        cache = list;
        return list;
    }

    private static InputStream resource(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(path.startsWith("/") ? path.substring(1) : path) : null;
        return in != null ? in : Icons.class.getResourceAsStream(path);
    }

    /** Icon of the given size, or the next larger/smaller one; null if there are none. */
    public static Image icon(int size) {
        Image best = null;
        for (Image img : appIcons()) {
            best = img;
            if (img.getWidth(null) >= size) break;
        }
        return best;
    }

    /** Largest icon, e.g. for the macOS dock or the About dialog. */
    public static Image largest() {
        List<Image> l = appIcons();
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    /** Sets the dock/taskbar icon where the platform supports it (macOS). */
    public static void installTaskbarIcon() {
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Image img = largest();
                if (img != null) Taskbar.getTaskbar().setIconImage(img);
            }
        } catch (Exception ignored) {
            // not supported on this platform
        }
    }
}
