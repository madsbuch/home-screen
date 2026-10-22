package dk.natmad.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.ImageButton;
import android.widget.Toast;

/** A small icon; the surrounding 48dp target remains easy to tap. */
final class DisplayModeControl extends ImageButton {
    private final Activity activity;
    private final DisplayMode mode;
    private final Runnable refresh = this::refresh;

    DisplayModeControl(Activity activity) {
        super(activity);
        this.activity = activity;
        mode = new DisplayMode(activity);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        setScaleType(ScaleType.CENTER_INSIDE);
        android.util.TypedValue background = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, background, true);
        setBackgroundResource(background.resourceId);
        setOnClickListener(view -> toggle());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(refresh);
        super.onDetachedFromWindow();
    }

    private void refresh() {
        removeCallbacks(refresh);
        try {
            mode.reconcile();
            long left = mode.remaining();
            setImageResource(left > 0 ? R.drawable.ic_display_color : R.drawable.ic_display_monochrome);
            String label = !mode.hasAccess() ? "Set up monochrome" : left > 0 ? "Use monochrome" : "Turn on colors";
            setContentDescription(label);
            setTooltipText(label);
            if (left > 0) postDelayed(refresh, left);
        } catch (RuntimeException error) {
            setImageResource(R.drawable.ic_display_monochrome);
            setContentDescription("Display control unavailable. Check monochrome setup.");
        }
    }

    private void toggle() {
        if (!mode.hasAccess()) {
            new AlertDialog.Builder(activity).setTitle("Set up phone-wide monochrome")
                .setMessage("Android needs a one-time grant from a computer with USB debugging enabled. "
                    + "Connect your phone and run:\n\nadb shell pm grant dk.natmad.home android.permission.WRITE_SECURE_SETTINGS"
                    + "\n\nThen return here. Monochrome becomes the default; the icon switches colors on or off. "
                    + "This uses Android’s color correction setting and replaces any existing color correction.")
                .setPositiveButton("Done", (dialog, which) -> refresh()).show();
            return;
        }
        if (mode.remaining() == 0 && !mode.hasAlarmAccess()) {
            new AlertDialog.Builder(activity).setTitle("Allow automatic monochrome")
                .setMessage("Allow Quiet Home under Alarms & reminders so it can check how long colors have been on, even while another app is open or the phone is asleep. Then return and tap the icon again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open settings", (dialog, which) -> {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + activity.getPackageName())));
                    } catch (ActivityNotFoundException error) {
                        Toast.makeText(activity, "Open Android Settings → Apps → Special app access → Alarms & reminders.", Toast.LENGTH_LONG).show();
                    }
                }).show();
            return;
        }
        try {
            if (mode.remaining() > 0) mode.monochrome();
            else mode.colors();
        } catch (RuntimeException error) {
            Toast.makeText(activity, "Could not change display colors. Check monochrome setup.", Toast.LENGTH_LONG).show();
        }
        refresh();
    }
}
