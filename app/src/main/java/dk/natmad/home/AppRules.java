package dk.natmad.home;

import java.text.Normalizer;
import java.util.Locale;

/** Name matching is used only to search the app drawer, never to identify home apps. */
public final class AppRules {
    public static boolean matchesSearch(String query, String label) {
        return normalize(label).contains(normalize(query));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
