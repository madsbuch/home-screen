package dk.natmad.home;

/** Edit this ordered list, rebuild, and reinstall to change the home screen. */
public final class HomeApps {
    private HomeApps() {}

    // Each row is one home slot. The first launchable package in the row wins.
    // App names are read from Android, not configured here.
    public static final String[][] PACKAGES = {
        {"org.thoughtcrime.securesms"},
        {"com.android.chrome"},
        {"com.google.android.dialer", "com.android.dialer"},
        {"com.google.android.apps.maps"},
        {"com.fastmail.app"},
        {"com.garmin.android.apps.connectmobile"},
        {"com.madsbuch.tally"},
        {"com.varg.workout"}
    };
}
