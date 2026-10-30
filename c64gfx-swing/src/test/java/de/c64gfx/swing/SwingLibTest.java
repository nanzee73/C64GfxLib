package de.c64gfx.swing;

import de.c64gfx.dither.Dither;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SwingLibTest {
    private static final Pattern FORMAT = Pattern.compile("%(\\.\\d+)?[sdf]");

    private static Properties load(Locale l) throws Exception {
        String f = "/de/c64gfx/swing/i18n/messages" + (l.getLanguage().equals("en") ? "" : "_" + l.getLanguage()) + ".properties";
        Properties p = new Properties();
        try (InputStream in = SwingLibTest.class.getResourceAsStream(f)) {
            assertNotNull(in, f);
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    private static List<String> formats(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = FORMAT.matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    @Test
    void allLibraryLanguagesHaveTheSameKeysAndPlaceholders() throws Exception {
        Properties en = load(Locale.ENGLISH);
        for (Locale l : AppConfig.LIBRARY_LANGUAGES) {
            Properties p = load(l);
            Set<String> a = new TreeSet<>(en.stringPropertyNames()), b = new TreeSet<>(p.stringPropertyNames());
            assertEquals(a, b, l.toString());
            for (String k : a) assertEquals(formats(en.getProperty(k)), formats(p.getProperty(k)), l + " " + k);
        }
    }

    @Test
    void everyDitherAndFitterEnumIsTranslated() throws Exception {
        Properties en = load(Locale.ENGLISH);
        for (Dither d : Dither.values()) assertTrue(en.containsKey("dither." + d.name()), d.name());
        for (ImageFitter.ScaleMode m : ImageFitter.ScaleMode.values()) assertTrue(en.containsKey("scale." + m.name()));
        for (ImageFitter.Filter f : ImageFitter.Filter.values()) assertTrue(en.containsKey("scalefilter." + f.name()));
        for (ImageFitter.Aspect a : ImageFitter.Aspect.values()) assertTrue(en.containsKey("aspect." + a.name()));
        for (int i = 0; i < 16; i++) assertTrue(en.containsKey("color." + i));
    }

    @Test
    void appBundleOverridesLibraryAndFallsBack() {
        AppConfig.install(new AppConfig("Test", "de/c64gfx/test", null, "de.c64gfx.swing.testapp",
                List.of(Locale.ENGLISH, Locale.GERMAN), false));
        I18n.init();
        I18n.setLocale(Locale.GERMAN);
        assertEquals("Mein OK", I18n.tr("ok"));          // overridden by the app
        assertEquals("Abbrechen", I18n.tr("cancel"));    // from the library
        assertEquals("nur.app", I18n.tr("nur.app"));     // unknown key → the key itself
        I18n.useSystemLocale();
    }
}
