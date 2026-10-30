package de.c64gfx.swing;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * User interface translations.
 * <p>
 * Texts are looked up first in the application's bundle ({@link AppConfig#messagesBundle()}), then in the library's
 * own bundle {@code de/c64gfx/swing/i18n/messages*.properties} (shared texts: OK/Cancel, colour names, dither names,
 * file filters, palette editor, slider tips …). All bundles are UTF-8; English is the base bundle.
 * <p>
 * Components register their texts with {@code bind(...)}; switching the language re-applies all bindings,
 * so the UI changes language without a restart.
 * <p>
 * Without a stored choice the UI uses the system language ({@link AppConfig#followSystemLanguage()}) or the first
 * configured language. Once the user picks a language explicitly, that choice is stored and wins.
 * Swing's own dialogs (file chooser, color chooser, message boxes) follow via the default locale.
 */
public final class I18n {
    private static final String LIBRARY_BUNDLE = "de.c64gfx.swing.i18n.messages";
    private static final String PREF_KEY = "language";
    /** The operating system language, captured before the app changes the default locale. */
    private static final Locale SYSTEM = Locale.getDefault();
    private static final List<Runnable> bindings = new ArrayList<>();
    private static Locale locale;
    private static boolean followsSystem = true;
    private static ResourceBundle appBundle, libBundle;

    private I18n() {}

    /** Supported UI languages of the application. */
    public static List<Locale> supportedLocales() { return AppConfig.current().languages(); }

    /** Initializes from the stored choice, otherwise as configured in {@link AppConfig}. */
    public static synchronized void init() {
        String stored = null;
        try {
            stored = AppConfig.prefs().get(PREF_KEY, null);
        } catch (Exception ignored) {
            // preferences not available
        }
        boolean hasChoice = stored != null && !stored.isBlank();
        followsSystem = !hasChoice && AppConfig.current().followSystemLanguage();
        if (hasChoice) apply(Locale.forLanguageTag(stored));
        else apply(followsSystem ? SYSTEM : supportedLocales().get(0));
    }

    /** Supported locale for the given one (same language), the first configured language if not supported. */
    public static Locale supported(Locale l) {
        for (Locale s : supportedLocales()) if (s.getLanguage().equals(l.getLanguage())) return s;
        return supportedLocales().get(0);
    }

    /** Language the UI would use when following the system. */
    public static Locale systemLocale() { return supported(SYSTEM); }

    public static boolean followsSystem() { return followsSystem; }

    private static void apply(Locale l) {
        locale = supported(l);
        Locale.setDefault(locale);
        JComponent.setDefaultLocale(locale);
        ResourceBundle.Control control =
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        String app = AppConfig.current().messagesBundle();
        appBundle = app == null ? null : ResourceBundle.getBundle(app, locale, contextLoader(), control);
        libBundle = ResourceBundle.getBundle(LIBRARY_BUNDLE, locale, I18n.class.getClassLoader(), control);
    }

    private static ClassLoader contextLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : I18n.class.getClassLoader();
    }

    /** Switches to a fixed language, stores the choice and updates every bound text. */
    public static synchronized void setLocale(Locale l) {
        followsSystem = false;
        apply(l);
        store(locale.toLanguageTag());
        refresh();
    }

    /** Follows the system language again (forgets the stored choice) and updates every bound text. */
    public static synchronized void useSystemLocale() {
        followsSystem = true;
        apply(SYSTEM);
        store(null);
        refresh();
    }

    private static void store(String tag) {
        try {
            if (tag == null) AppConfig.prefs().remove(PREF_KEY);
            else AppConfig.prefs().put(PREF_KEY, tag);
        } catch (Exception ignored) {
            // preferences not available
        }
    }

    private static void refresh() {
        for (Runnable r : new ArrayList<>(bindings)) r.run();
    }

    public static synchronized Locale locale() {
        if (locale == null) init();
        return locale;
    }

    /** Name of a language in that language, e.g. "Deutsch", "Polski". */
    public static String nativeName(Locale l) {
        String n = l.getDisplayLanguage(l);
        return n.isEmpty() ? l.toLanguageTag() : n.substring(0, 1).toUpperCase(l) + n.substring(1);
    }

    /** Whether the key exists in the application's or the library's bundle. */
    public static boolean has(String key) {
        if (libBundle == null) init();
        return (appBundle != null && appBundle.containsKey(key)) || libBundle.containsKey(key);
    }

    /** Translated text; with arguments it is used as a {@link String#format} pattern. */
    public static String tr(String key, Object... args) {
        if (libBundle == null) init();
        String s = null;
        if (appBundle != null) {
            try {
                s = appBundle.getString(key);
            } catch (MissingResourceException ignored) {
                // not an application text
            }
        }
        if (s == null) {
            try {
                s = libBundle.getString(key);
            } catch (MissingResourceException e) {
                s = key;
            }
        }
        return args.length == 0 ? s : String.format(locale, s, args);
    }

    /** Text for an enum constant: key {@code prefix + "." + name()}. */
    public static String tr(String prefix, Enum<?> e) { return tr(prefix + "." + e.name()); }

    /** Runs the action now and again after every language switch. */
    public static synchronized void bind(Runnable r) {
        bindings.add(r);
        r.run();
    }

    public static <T extends AbstractButton> T text(T b, String key) {
        bind(() -> b.setText(tr(key)));
        return b;
    }

    public static JLabel label(String key) {
        JLabel l = new JLabel();
        bind(() -> l.setText(tr(key)));
        return l;
    }

    public static <T extends JComponent> T tip(T c, String key, Object... args) {
        bind(() -> c.setToolTipText(tr(key, args)));
        return c;
    }

    public static TitledBorder titled(JComponent owner, String key) {
        TitledBorder b = new TitledBorder("");
        bind(() -> {
            b.setTitle(tr(key));
            owner.repaint();
        });
        return b;
    }

    /** List renderer that shows {@code text(value)}, e.g. {@code I18n.renderer(d -> tr("dither", (Dither) d))}. */
    public static ListCellRenderer<Object> renderer(Function<Object, String> text) {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : text.apply(value), index,
                        selected, focus);
            }
        };
    }

    /** List renderer for enum constants with keys {@code prefix + "." + name()}. */
    public static ListCellRenderer<Object> enumRenderer(String prefix) {
        return renderer(v -> v instanceof Enum<?> e ? tr(prefix, e) : String.valueOf(v));
    }
}
