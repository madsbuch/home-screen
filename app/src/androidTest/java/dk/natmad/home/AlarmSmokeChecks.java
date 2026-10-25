package dk.natmad.home;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import java.util.Calendar;
import java.util.TimeZone;

final class AlarmSmokeChecks {
    static void run(Instrumentation test, Activity activity) {
        test.runOnMainSync(() -> {
            activity.onBackPressed();
            ViewGroup root = activity.findViewById(android.R.id.content);
            AlarmControl home = findControl(root);
            require(home != null && home.isClickable(), "Home must expose alarms");
            require(home.getContentDescription().toString().startsWith("Open alarms."),
                "Alarm action and state must be accessible");
            require(home.getMinHeight() >= Math.round(48 * activity.getResources().getDisplayMetrics().density),
                "Alarm row needs a 48dp tap target");
            AlarmManager.AlarmClockInfo actual = activity.getSystemService(AlarmManager.class).getNextAlarmClock();
            require(home.getContentDescription().toString().startsWith(actual == null
                    ? "Open alarms. No alarm set." : "Open alarms. Next alarm:"),
                "Home must reflect the system's current next alarm");

            Fixture context = new Fixture(activity);
            AlarmManager.AlarmClockInfo[] next = {null};
            AlarmControl control = new AlarmControl(context, () -> next[0]);
            String originalFormat = Settings.System.getString(activity.getContentResolver(), Settings.System.TIME_12_24);
            TimeZone originalZone = TimeZone.getDefault();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                Settings.System.putString(activity.getContentResolver(), Settings.System.TIME_12_24, "24");
                control.start();
                control.start();
                require(context.registrations == 1, "Repeated start must not leak receivers");
                require(control.getText().length() == 0, "No alarm should show only the small icon");
                require(control.getCompoundDrawablesRelative()[0] != null, "Alarm shortcut needs an icon");
                control.performClick();
                require(AlarmClock.ACTION_SHOW_ALARMS.equals(context.opened.getAction()),
                    "Alarm access must work even with no scheduled alarm");

                Calendar trigger = Calendar.getInstance();
                trigger.set(2030, Calendar.JANUARY, 2, 17, 5, 0);
                next[0] = new AlarmManager.AlarmClockInfo(trigger.getTimeInMillis(), null);
                context.change(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
                require(control.getText().toString().contains("17:05"), "Alarm must use the device's 24-hour setting");
                Settings.System.putString(activity.getContentResolver(), Settings.System.TIME_12_24, "12");
                context.change(Intent.ACTION_TIME_CHANGED);
                require(control.getText().toString().contains("5:05") && !control.getText().toString().contains("17:05"),
                    "Alarm must follow changes to 12-hour format");
                Settings.System.putString(activity.getContentResolver(), Settings.System.TIME_12_24, "24");
                TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"));
                context.change(Intent.ACTION_TIMEZONE_CHANGED);
                require(control.getText().toString().contains("19:05"), "Time zone changes must update the displayed alarm");

                next[0] = null;
                context.change(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
                require(control.getText().length() == 0, "Cancelled alarms must clear the time indicator");
                context.unavailable = true;
                control.performClick(); // No handler and no alarm must not crash.
                control.stop();
                control.stop();
                require(context.receiver == null && context.removals == 1, "Stop must unregister exactly once");
                next[0] = new AlarmManager.AlarmClockInfo(trigger.getTimeInMillis(), null);
                control.start();
                require(control.getText().toString().contains("19:05"), "Resuming must query changes made while away");
                root.addView(control);
                root.removeView(control);
                require(context.receiver == null, "Detached alarm controls must stop observing");
            } finally {
                control.stop();
                Settings.System.putString(activity.getContentResolver(), Settings.System.TIME_12_24, originalFormat);
                TimeZone.setDefault(originalZone);
                activity.onBackPressed();
            }
        });
    }

    private static final class Fixture extends ContextWrapper {
        BroadcastReceiver receiver;
        IntentFilter filter;
        Intent opened;
        int registrations;
        int removals;
        boolean unavailable;

        Fixture(Context context) { super(context); }
        @Override public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            return recordReceiver(receiver, filter);
        }
        private Intent recordReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            this.receiver = receiver;
            this.filter = filter;
            registrations++;
            return null;
        }
        @Override public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
            return recordReceiver(receiver, filter);
        }
        @Override public void unregisterReceiver(BroadcastReceiver receiver) {
            require(this.receiver == receiver, "Unregistered the wrong receiver");
            this.receiver = null;
            removals++;
        }
        @Override public void startActivity(Intent intent) {
            if (unavailable) throw new ActivityNotFoundException();
            opened = intent;
        }
        void change(String action) {
            require(filter.hasAction(action), "Missing subscription for " + action);
            receiver.onReceive(this, new Intent(action));
        }
    }

    private static AlarmControl findControl(View view) {
        if (view instanceof AlarmControl) return (AlarmControl) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                AlarmControl found = findControl(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
