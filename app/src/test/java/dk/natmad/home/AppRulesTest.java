package dk.natmad.home;

/** Dependency-free checks; run scripts/test-rules.sh. */
public class AppRulesTest {
    public static void main(String[] args) {
        check(AppRules.matchesSearch("", "Signal"), "empty query shows all apps");
        check(AppRules.matchesSearch("   ", "Signal"), "whitespace query shows all apps");
        check(AppRules.matchesSearch(" CONNECT ", "Garmin Connect"), "search finds substrings ignoring case and whitespace");
        check(AppRules.matchesSearch("mail", "FastMail"), "search finds a suffix");
        check(AppRules.matchesSearch("Cafe\u0301", "Café"), "Unicode equivalence");
        check(!AppRules.matchesSearch("maps", "Signal"), "unrelated app is excluded");
        check(!AppRules.matchesSearch("Signal extra", "Signal"), "overlong query is excluded");
        check(AppFriction.forPackage("com.google.android.youtube") == AppFriction.Level.HARD, "YouTube is hard");
        check(AppFriction.forPackage("com.instagram.android") == AppFriction.Level.HARD, "Instagram is hard");
        check(AppFriction.forPackage("com.facebook.katana") == AppFriction.Level.HARD, "Facebook is hard");
        check(AppFriction.forPackage("com.openai.chatgpt") == AppFriction.Level.SLIGHT, "ChatGPT has a short hold");
        check(AppFriction.forPackage("com.anthropic.claude") == AppFriction.Level.SLIGHT, "Claude has a short hold");
        check(AppFriction.forPackage("com.google.android.apps.bard") == AppFriction.Level.SLIGHT, "Gemini has a short hold");
        check(AppFriction.forPackage("org.thoughtcrime.securesms") == AppFriction.Level.NONE, "Signal remains immediate");
        check(AppFriction.forPackage("com.whatsapp") == AppFriction.Level.NONE, "Messaging remains immediate");
        check(AppFriction.forPackage("com.openai.chatgpt.unrelated") == AppFriction.Level.NONE, "IDs match exactly");
        check(AppFriction.forPackage("YouTube") == AppFriction.Level.NONE, "Labels never determine friction");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (String id : AppFriction.HARD_PACKAGES) check(ids.add(id), "Duplicate hard package: " + id);
        for (String id : AppFriction.SLIGHT_PACKAGES) check(ids.add(id), "Overlapping package: " + id);
        System.out.println("All app-search and friction-policy checks passed.");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
