package dk.natmad.home;

/** Edit this ordered list, rebuild, and reinstall to change the home screen. */
public final class HomeApps {
    private HomeApps() {}

    // Each row is one home slot. The first launchable package in the row wins.
    // App names are read from Android, not configured here.
    public static final String[][] PACKAGES = {
        {"com.google.android.dialer", "com.android.dialer"},
        {"dk.rejsekort.digitalrejsekort"},
        {"com.garmin.android.apps.connectmobile"},
        {"com.madsbuch.tally"},
        {"com.varg.workout"}
    };
}
