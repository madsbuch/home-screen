package dk.natmad.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ImageButton;
import android.widget.Toast;

/** A small icon; the surrounding 48dp target remains easy to tap. */
final class DisplayModeControl extends ImageButton {
    private final Activity activity;
    private final DisplayMode mode;

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

    private void refresh() {
        try {
            mode.reconcile();
            long left = mode.remaining();
            setImageResource(left > 0 ? R.drawable.ic_display_color : R.drawable.ic_display_monochrome);
            String label = !mode.hasAccess() ? "Set up monochrome" : left > 0 ? "Use monochrome" : "Turn on colors";
            setContentDescription(label);
            setTooltipText(label);
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
        try {
            if (mode.colorsSelected()) mode.monochrome();
            else mode.colors();
        } catch (RuntimeException error) {
            Toast.makeText(activity, "Could not change display colors. Check monochrome setup.", Toast.LENGTH_LONG).show();
        }
        refresh();
    }
}
