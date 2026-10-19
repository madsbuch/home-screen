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
        System.out.println("All app-search checks passed.");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
