package dk.natmad.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.AlarmClock;
import android.text.format.DateFormat;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.Toast;
import java.util.Date;
import java.util.Locale;
import java.util.function.Supplier;

/** Opens the alarm app and observes the next system-published alarm while home is visible. */
final class AlarmControl extends Button {
    private final Supplier<AlarmManager.AlarmClockInfo> nextAlarm;
    private boolean listening;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };

    AlarmControl(Context context) {
        this(context, () -> context.getSystemService(AlarmManager.class).getNextAlarmClock());
    }

    AlarmControl(Context context, Supplier<AlarmManager.AlarmClockInfo> nextAlarm) {
        super(context);
        this.nextAlarm = nextAlarm;
        setAllCaps(false);
        setTextSize(14);
        setTextColor(0xff999999);
        setIncludeFontPadding(false);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        setMinHeight(dp(48));
        setMinimumHeight(dp(48));
        setMinWidth(dp(48));
        setMinimumWidth(dp(48));
        setMaxWidth(dp(160));
        // Match the display control's 24dp icon centered in its final 48dp.
        // Keep the time on the leading side so the icon never moves between states.
        setPaddingRelative(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.Drawable icon = context.getDrawable(R.drawable.ic_alarm);
        icon.setBounds(0, 0, dp(24), dp(24));
        setCompoundDrawablesRelative(null, null, icon, null);
        android.util.TypedValue background = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
        setBackgroundResource(background.resourceId);
        setTooltipText("Open alarms");
        setOnClickListener(ignored -> openAlarms());
        refresh();
    }

    void start() {
        if (listening) return;
        IntentFilter filter = new IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        if (Build.VERSION.SDK_INT >= 33) {
            getContext().registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(changes, filter);
        }
        listening = true;
        refresh();
    }

    void stop() {
        if (!listening) return;
        getContext().unregisterReceiver(changes);
        listening = false;
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void refresh() {
        AlarmManager.AlarmClockInfo alarm = nextAlarm.get();
        String label = "";
        String description = "Open alarms. No alarm set.";
        if (alarm != null) {
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            String pattern = DateFormat.getBestDateTimePattern(locale, "EEE MMM d");
            Date trigger = new Date(alarm.getTriggerTime());
            String time = DateFormat.getTimeFormat(getContext()).format(trigger);
            label = DateFormat.format("EEE", trigger) + " " + time;
            description = "Open alarms. Next alarm: " + DateFormat.format(pattern, trigger) + ", " + time;
        }
        setCompoundDrawablePadding(alarm == null ? 0 : dp(8));
        setText(label);
        setContentDescription(description);
    }

    private void openAlarms() {
        // Launch directly: package visibility must not hide an otherwise valid handler.
        try {
            getContext().startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
            return;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            // Try the Clock app's normal entry point next, whether an alarm is set or not.
        }
        // Some vendor clocks omit SHOW_ALARMS or protect it with a permission.
        // Discover their alarm capability, then open their normal launcher entry.
        // Do not send SET_ALARM: tapping this shortcut must never create an alarm.
        for (Intent launch : AlarmApps.launchIntents(getContext().getPackageManager())) {
            try {
                getContext().startActivity(launch);
                return;
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                // An app can disappear or be disabled after discovery; try the next.
            }
        }
        // A successful send does not guarantee that a vendor shortcut opens a screen.
        // Use it only when no normal Clock entry point is available, so setting an
        // alarm cannot bypass a working app launch (notably on OnePlus).
        AlarmManager.AlarmClockInfo alarm = nextAlarm.get();
        PendingIntent show = alarm == null ? null : alarm.getShowIntent();
        if (show != null) {
            try {
                show.send();
                return;
            } catch (PendingIntent.CanceledException | SecurityException unavailable) {
                // The alarm or its app may have been removed since the last refresh.
            }
        }
        refresh();
        Toast.makeText(getContext(), "No alarm app is available.", Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
