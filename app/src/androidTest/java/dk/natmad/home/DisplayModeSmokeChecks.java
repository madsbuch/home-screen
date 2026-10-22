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
            test.runOnMainSync(() -> {
                activity.onBackPressed();
                DisplayModeControl control = findControl(activity.findViewById(android.R.id.content));
                require(control != null, "Home must have a display icon");
                require(control.getParent() instanceof android.widget.LinearLayout, "Icon needs a clock row");
                android.widget.LinearLayout header = (android.widget.LinearLayout) control.getParent();
                require(header.getChildAt(0) instanceof android.widget.TextClock && header.getChildAt(1) == control,
                    "Icon must sit next to the clock");
                require(control.getLayoutParams().width == Math.round(48 * activity.getResources().getDisplayMetrics().density),
                    "Icon must have a compact tap target");
                require("Turn on colors".contentEquals(control.getContentDescription()), "Icon needs an accessible action");
                control.performClick();
                require("Use monochrome".contentEquals(control.getContentDescription()), "Icon action must update after toggling");
            });
            require(!monochrome(context), "Colors must disable system grayscale");
            long enabledAt = prefs.getLong("enabled_at", 0);
            require(enabledAt > 0 && enabledAt <= System.currentTimeMillis(), "Persist the actual switch-on time");
            long left = mode.remaining();
            require(left > ColorWindow.DURATION - 5000 && left <= ColorWindow.DURATION, "Colors must last 12 hours");
            new DisplayMode(context).reconcile();
            require(mode.remaining() <= left, "Reopening must preserve the original switch-on time");
            require(prefs.getLong("enabled_at", 0) == enabledAt, "Checks must not rewrite the timestamp");
            new DisplayMode().onReceive(context, new Intent("dk.natmad.home.RESET_MONOCHROME"));
            require(!monochrome(context), "Stale alarm must not end a fresh color window");
            mode.monochrome();
            require(monochrome(context) && mode.remaining() == 0, "Manual toggle must clear the timestamp");

            mode.colors();
            long earlier = System.currentTimeMillis() - ColorWindow.DURATION + 60_000;
            prefs.edit().putLong("enabled_at", earlier).commit();
            new DisplayMode().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
            require(mode.remaining() > 55_000 && mode.remaining() <= 60_000, "Reboot checks the original switch-on time");
            require(prefs.getLong("enabled_at", 0) == earlier, "Boot must not rewrite the timestamp");

            // Upgrade an active color session from the released deadline-based version.
            prefs.edit().clear().putLong("wall", earlier + ColorWindow.DURATION)
                .putLong("elapsed", SystemClock.elapsedRealtime() + 60_000).putInt("boot", 1).commit();
            mode.reconcile();
            require(prefs.getLong("enabled_at", 0) == earlier && !prefs.contains("wall"), "Upgrade must retain the switch time");

            // Age the saved timestamp, exercising the actual OS wake-up and manifest receiver.
            prefs.edit().putLong("enabled_at", System.currentTimeMillis() - ColorWindow.DURATION + 1200).commit();
            mode.reconcile();
            test.runOnMainSync(() -> activity.startActivity(new Intent(Settings.ACTION_SETTINGS)));
            long timeout = SystemClock.elapsedRealtime() + 10_000;
            while (!monochrome(context) && SystemClock.elapsedRealtime() < timeout) SystemClock.sleep(100);
            require(monochrome(context), "Alarm must restore monochrome while another app is open");
            require(!prefs.contains("enabled_at"), "Check must clear the expired timestamp");
        } finally {
            mode.monochrome();
            test.runOnMainSync(() -> activity.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            test.waitForIdleSync();
        }
    }

    private static DisplayModeControl findControl(android.view.View view) {
        if (view instanceof DisplayModeControl) return (DisplayModeControl) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                DisplayModeControl result = findControl(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static boolean monochrome(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), "accessibility_display_daltonizer_enabled", 0) == 1
            && Settings.Secure.getInt(context.getContentResolver(), "accessibility_display_daltonizer", -1) == 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
