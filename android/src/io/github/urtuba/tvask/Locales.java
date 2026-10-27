package io.github.urtuba.tvask;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

// Locales applies the ui_language setting. Empty means the TV's language.
// It only changes tvask's own texts. The model answers in the language the
// user speaks.
final class Locales {
    // LANGUAGES are the UI languages with a translation, as language tags.
    static final String[] LANGUAGES = {"", "en", "tr"};

    private Locales() {
    }

    static String lang(Context c) {
        return Config.prefs(c).getString("ui_language", "");
    }

    static Context wrap(Context c) {
        String lang = lang(c);
        if (lang.isEmpty()) {
            return c;
        }
        Configuration conf = new Configuration(c.getResources().getConfiguration());
        conf.setLocale(Locale.forLanguageTag(lang));
        return c.createConfigurationContext(conf);
    }

    // name returns a language's own name, like "Türkçe".
    static String name(String tag) {
        Locale l = Locale.forLanguageTag(tag);
        String n = l.getDisplayLanguage(l);
        return n.isEmpty() ? tag : n.substring(0, 1).toUpperCase(l) + n.substring(1);
    }
}
