package dk.natmad.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

final class DisplayModeSmokeChecks {
    static void run(Instrumentation test, Activity activity) {
        Context context = test.getTargetContext();
        DisplayMode mode = new DisplayMode(context);
        require(mode.hasAccess(), "Grant WRITE_SECURE_SETTINGS to run display checks");
        require(mode.hasAlarmAccess(), "Allow Alarms & reminders to run display checks");
        SharedPreferences prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("display_mode", Context.MODE_PRIVATE);
        try {
            mode.monochrome();
            require(monochrome(context), "Default must be whole-phone monochrome");
            mode.colors();
            require(!monochrome(context), "Colors must disable system grayscale");
            long left = mode.remaining();
            require(left > ColorWindow.DURATION - 5000 && left <= ColorWindow.DURATION, "Colors must last 12 hours");
            new DisplayMode(context).reconcile();
            require(mode.remaining() <= left, "Reopening must not restart the timer");
            new DisplayMode().onReceive(context, new Intent("dk.natmad.home.RESET_MONOCHROME"));
            require(!monochrome(context), "Stale alarm must not end a fresh color window");
            mode.monochrome();
            require(monochrome(context) && mode.remaining() == 0, "Manual toggle must clear the deadline");

            mode.colors();
            prefs.edit().putInt("boot", -10).putLong("wall", System.currentTimeMillis() + 60_000).commit();
            new DisplayMode().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
            require(mode.remaining() > 55_000 && mode.remaining() <= 60_000, "Reboot must preserve remaining time");

            // Shorten only the saved deadline, exercising the actual OS alarm and manifest receiver.
            prefs.edit().putLong("wall", System.currentTimeMillis() + 1200)
                .putLong("elapsed", SystemClock.elapsedRealtime() + 1200).commit();
            mode.reconcile();
            test.runOnMainSync(() -> activity.startActivity(new Intent(Settings.ACTION_SETTINGS)));
            long timeout = SystemClock.elapsedRealtime() + 10_000;
            while (!monochrome(context) && SystemClock.elapsedRealtime() < timeout) SystemClock.sleep(100);
            require(monochrome(context), "Alarm must restore monochrome while another app is open");
            require(!prefs.contains("wall"), "Alarm must clear the expired exception");
        } finally {
            mode.monochrome();
            test.runOnMainSync(() -> activity.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            test.waitForIdleSync();
        }
    }

    private static boolean monochrome(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), "accessibility_display_daltonizer_enabled", 0) == 1
            && Settings.Secure.getInt(context.getContentResolver(), "accessibility_display_daltonizer", -1) == 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
