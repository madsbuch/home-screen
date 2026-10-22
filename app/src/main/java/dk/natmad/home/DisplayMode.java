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

    private long enabledAt() {
        SharedPreferences prefs = preferences();
        if (!prefs.contains("enabled_at") && prefs.contains("wall")) {
            // Upgrade the previous deadline format once, retaining the original switch time.
            long started = prefs.getLong("wall", 0) - ColorWindow.DURATION;
            SharedPreferences.Editor edit = prefs.edit().clear();
            if (started > 0) edit.putLong("enabled_at", started);
            if (!edit.commit()) throw new IllegalStateException("Could not migrate color state");
        }
        return prefs.getLong("enabled_at", 0);
    }

    long remaining() {
        return ColorWindow.remaining(enabledAt(), System.currentTimeMillis());
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
            throw new IllegalStateException("Could not clear the color state");
        alarms().cancel(resetIntent());
    }

    void colors() {
        if (!hasAccess() || !hasAlarmAccess()) throw new IllegalStateException("Setup is incomplete");
        if (!preferences().edit().clear().putLong("enabled_at", System.currentTimeMillis()).commit())
            throw new IllegalStateException("Could not save when colors were enabled");
        try {
            // The wake-up only checks the timestamp; it does not hold the color state.
            schedule(SystemClock.elapsedRealtime() + remaining());
            apply(true);
        } catch (RuntimeException error) {
            monochrome();
            throw error;
        }
    }

    void reconcile() {
        if (!hasAccess()) return;
        long left = remaining();
        if (left == 0 || !hasAlarmAccess()) monochrome();
        else {
            try {
                schedule(SystemClock.elapsedRealtime() + left);
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
