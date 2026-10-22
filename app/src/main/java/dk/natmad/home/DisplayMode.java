package dk.natmad.home;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

/** Phone-wide grayscale with a persistent, bounded exception for colors. */
public final class DisplayMode extends BroadcastReceiver {
    private static final String ENABLED = "accessibility_display_daltonizer_enabled";
    private static final String MODE = "accessibility_display_daltonizer";
    private static final String RESET = "dk.natmad.home.RESET_MONOCHROME";
    private final Context context;

    public DisplayMode() { context = null; } // Android instantiates the receiver.
    DisplayMode(Context context) { this.context = context.getApplicationContext(); }

    private SharedPreferences preferences() {
        // The reset must also work after reboot before the phone is unlocked.
        return context.createDeviceProtectedStorageContext()
            .getSharedPreferences("display_mode", Context.MODE_PRIVATE);
    }

    boolean hasAccess() {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            == PackageManager.PERMISSION_GRANTED;
    }

    boolean hasAlarmAccess() {
        return Build.VERSION.SDK_INT < 31 || alarms().canScheduleExactAlarms();
    }

    private AlarmManager alarms() { return context.getSystemService(AlarmManager.class); }

    private int boot() {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    long remaining() {
        SharedPreferences prefs = preferences();
        return ColorWindow.remaining(prefs.getLong("wall", 0), prefs.getLong("elapsed", 0),
            prefs.getInt("boot", -2), System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot());
    }

    private PendingIntent resetIntent() {
        return PendingIntent.getBroadcast(context, 0,
            new Intent(context, DisplayMode.class).setAction(RESET),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void schedule(long elapsedDeadline) {
        alarms().setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            elapsedDeadline, resetIntent());
    }

    private void apply(boolean colors) {
        // These AOSP secure settings are hidden constants, so use their stable stored names.
        if (!colors && !Settings.Secure.putInt(context.getContentResolver(), MODE, 0))
            throw new IllegalStateException("Could not select monochrome");
        if (!Settings.Secure.putInt(context.getContentResolver(), ENABLED, colors ? 0 : 1))
            throw new IllegalStateException("Could not change display colors");
    }

    void monochrome() {
        apply(false);
        if (!preferences().edit().clear().commit())
            throw new IllegalStateException("Could not clear the color deadline");
        alarms().cancel(resetIntent());
    }

    void colors() {
        if (!hasAccess() || !hasAlarmAccess()) throw new IllegalStateException("Setup is incomplete");
        if (!preferences().edit().putLong("wall", System.currentTimeMillis() + ColorWindow.DURATION)
                .putLong("elapsed", SystemClock.elapsedRealtime() + ColorWindow.DURATION)
                .putInt("boot", boot()).commit())
            throw new IllegalStateException("Could not save the reset time");
        try {
            // Never enable colors until both the deadline and the reset alarm exist.
            schedule(preferences().getLong("elapsed", 0));
            apply(true);
        } catch (RuntimeException error) {
            monochrome();
            throw error;
        }
    }

    void reconcile() {
        if (!hasAccess()) return;
        SharedPreferences prefs = preferences();
        long wallNow = System.currentTimeMillis();
        long elapsedNow = SystemClock.elapsedRealtime();
        int currentBoot = boot();
        long left = ColorWindow.remaining(prefs.getLong("wall", 0), prefs.getLong("elapsed", 0),
            prefs.getInt("boot", -2), wallNow, elapsedNow, currentBoot);
        if (left == 0 || !hasAlarmAccess()) monochrome();
        else {
            // Re-anchor elapsed time after reboot without extending the saved window.
            if (!prefs.edit().putLong("wall", wallNow + left)
                    .putLong("elapsed", elapsedNow + left)
                    .putInt("boot", currentBoot).commit()) {
                monochrome();
                return;
            }
            try {
                schedule(elapsedNow + left);
                apply(true);
            } catch (RuntimeException error) {
                monochrome();
                throw error;
            }
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!RESET.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) return;
        try { new DisplayMode(context).reconcile(); }
        catch (RuntimeException error) {
            android.util.Log.e("QuietHome", "Unable to restore display mode", error);
        }
    }
}
