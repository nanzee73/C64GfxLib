package de.c64gfx.swing;

import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * What the library needs to know about the application using it. Call {@link #install} once at start-up,
 * before {@link I18n#init()} or any other class of this package is used.
 *
 * @param name                 application name (window titles, macOS menu)
 * @param prefsNode            node in the Java user preferences, e.g. {@code "de/flipperj"}
 * @param iconPrefix           resource path of the icon PNGs without size, e.g. {@code "/de/flipperj/icons/flipperj-"}
 *                             (files {@code <prefix>16.png} … {@code <prefix>512.png})
 * @param messagesBundle       the application's own translations, e.g. {@code "de.flipperj.i18n.messages"};
 *                             keys not found there are looked up in the library's bundle
 * @param languages            supported UI languages, the first one is the fallback
 * @param followSystemLanguage without a stored choice: true = system language, false = first of {@code languages}
 */
public record AppConfig(String name, String prefsNode, String iconPrefix, String messagesBundle,
                        List<Locale> languages, boolean followSystemLanguage) {

    /** All languages the library itself is translated into. */
    public static final List<Locale> LIBRARY_LANGUAGES = List.of(
            Locale.ENGLISH, Locale.GERMAN, Locale.forLanguageTag("pl"), Locale.forLanguageTag("sv"),
            Locale.forLanguageTag("nl"));

    private static AppConfig current = new AppConfig("C64GfxLib", "de/c64gfx", null, null,
            List.of(Locale.ENGLISH), false);

    public AppConfig {
        if (languages == null || languages.isEmpty()) languages = List.of(Locale.ENGLISH);
        languages = List.copyOf(languages);
    }

    public static synchronized void install(AppConfig config) { current = config; }

    public static synchronized AppConfig current() { return current; }

    /** The application's preferences node. */
    public static Preferences prefs() { return Preferences.userRoot().node(current().prefsNode()); }
}
