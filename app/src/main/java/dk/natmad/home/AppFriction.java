package dk.natmad.home;

/** Edit package IDs here, then rebuild to change the opening friction. */
public final class AppFriction {
    private AppFriction() {}

    public enum Level { NONE, SLIGHT, HARD }
    public static final long HARD_WAIT_MS = 30_000;
    public static final long SLIGHT_HOLD_MS = 3_000;

    public static final String[] HARD_PACKAGES = {
        "com.google.android.youtube",
        "com.instagram.android",
        "com.instagram.lite",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.barcelona",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.snapchat.android",
        "com.pinterest",
        "com.linkedin.android",
        "xyz.blueskyweb.app",
        "org.joinmastodon.android",
        "com.tumblr"
    };

    public static final String[] SLIGHT_PACKAGES = {
        "com.openai.chatgpt",
        "com.anthropic.claude",
        "com.google.android.apps.bard",
        "ai.perplexity.app.android",
        "com.microsoft.copilot",
        "ai.x.grok",
        "com.deepseek.chat",
        "com.poe.android",
        "ai.mistral.chat",
        "ai.character.app"
    };

    public static Level forPackage(String packageId) {
        for (String id : HARD_PACKAGES) if (id.equals(packageId)) return Level.HARD;
        for (String id : SLIGHT_PACKAGES) if (id.equals(packageId)) return Level.SLIGHT;
        return Level.NONE;
    }
}
