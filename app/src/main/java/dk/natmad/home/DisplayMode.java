package dk.natmad.home;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Checks the saved color switch-on time when the launcher returns to the foreground. */
final class DisplayMode {
    private static final String ENABLED = "accessibility_display_daltonizer_enabled";
    private static final String MODE = "accessibility_display_daltonizer";
    private static final String RESET = "dk.natmad.home.RESET_MONOCHROME";
    private final Context context;

    DisplayMode(Context context) { this.context = context.getApplicationContext(); }

    private SharedPreferences preferences() {
        // Keep the existing storage location so installed versions retain their color state.
        return context.createDeviceProtectedStorageContext()
            .getSharedPreferences("display_mode", Context.MODE_PRIVATE);
    }

    boolean hasAccess() {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            == PackageManager.PERMISSION_GRANTED;
    }

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

    boolean colorsSelected() { return enabledAt() > 0; }

    private void cancelLegacyAlarm() {
        // Older releases scheduled a broadcast. Remove it without creating a new one.
        PendingIntent legacy = PendingIntent.getBroadcast(context, 0,
            new Intent().setClassName(context, "dk.natmad.home.DisplayMode").setAction(RESET),
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (legacy != null) {
            context.getSystemService(AlarmManager.class).cancel(legacy);
            legacy.cancel();
        }
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
        cancelLegacyAlarm();
    }

    void colors() {
        if (!hasAccess()) throw new IllegalStateException("Setup is incomplete");
        if (!preferences().edit().clear().putLong("enabled_at", System.currentTimeMillis()).commit())
            throw new IllegalStateException("Could not save when colors were enabled");
        try {
            apply(true);
        } catch (RuntimeException error) {
            monochrome();
            throw error;
        }
    }

    void reconcile() {
        cancelLegacyAlarm();
        if (!hasAccess()) return;
        if (remaining() == 0) monochrome();
        else apply(true);
    }
}
