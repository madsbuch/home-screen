package dk.natmad.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Date;

final class DisplayModeControl extends LinearLayout {
    private final Activity activity;
    private final DisplayMode mode;
    private final Button toggle;
    private final TextView status;
    private final Runnable refresh = this::refresh;

    DisplayModeControl(Activity activity) {
        super(activity);
        this.activity = activity;
        mode = new DisplayMode(activity);
        setOrientation(VERTICAL);
        toggle = new Button(activity);
        toggle.setTextSize(16);
        ControlStyle.pill(toggle, false);
        addView(toggle, new LayoutParams(-1, -2));
        status = new TextView(activity);
        status.setTextColor(0xff999999);
        status.setTextSize(12);
        status.setPadding(0, 4, 0, 4);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(status, new LayoutParams(-1, -2));
        toggle.setOnClickListener(view -> toggle());
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
            toggle.setText(!mode.hasAccess() ? "Set up monochrome" : left > 0 ? "Use monochrome" : "Colors for 12 hours");
            status.setText(!mode.hasAccess() ? "Control colors across your whole phone."
                : left > 0 ? "Monochrome returns at " + android.text.format.DateFormat.getTimeFormat(activity)
                    .format(new Date(System.currentTimeMillis() + left))
                : "Monochrome · whole phone");
            if (left > 0) postDelayed(refresh, left);
        } catch (RuntimeException error) {
            toggle.setText("Set up monochrome");
            status.setText("Display control unavailable. Tap to check setup.");
        }
    }

    private void toggle() {
        if (!mode.hasAccess()) {
            new AlertDialog.Builder(activity).setTitle("Set up phone-wide monochrome")
                .setMessage("Android needs a one-time grant from a computer with USB debugging enabled. "
                    + "Connect your phone and run:\n\nadb shell pm grant dk.natmad.home android.permission.WRITE_SECURE_SETTINGS"
                    + "\n\nThen return here. Monochrome becomes the default; the button enables colors for 12 hours. "
                    + "This uses Android’s color correction setting and replaces any existing color correction.")
                .setPositiveButton("Done", (dialog, which) -> refresh()).show();
            return;
        }
        if (mode.remaining() == 0 && !mode.hasAlarmAccess()) {
            new AlertDialog.Builder(activity).setTitle("Allow the 12-hour reset")
                .setMessage("Allow Quiet Home under Alarms & reminders so monochrome can return while other apps are open or the phone is asleep. Then tap Colors for 12 hours again.")
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
