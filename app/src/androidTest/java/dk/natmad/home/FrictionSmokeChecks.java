package dk.natmad.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Real-duration checks against the same view used to open apps. */
final class FrictionSmokeChecks {
    static void run(Instrumentation test, Activity activity) {
        LaunchGate[] gate = {null};
        int[] opened = {0};
        test.runOnMainSync(() -> {
            gate[0] = new LaunchGate(activity, "Hard app", AppFriction.Level.HARD,
                () -> opened[0]++, () -> {});
            activity.setContentView(gate[0]);
        });
        test.waitForIdleSync();
        test.runOnMainSync(() -> {
            require(gate[0].hasWindowFocus(), "Test window needs focus");
            Button open = button(gate[0]);
            require(!open.isEnabled(), "Hard app must initially be disabled");
            open.performClick();
            require(opened[0] == 0, "Early activation bypassed wait");
        });
        SystemClock.sleep(1_100);
        test.runOnMainSync(() -> {
            gate[0].onWindowFocusChanged(false);
            require(!button(gate[0]).isEnabled(), "Focus loss must discard progress");
            gate[0].onWindowFocusChanged(true);
            require(((TextView) gate[0].getChildAt(1)).getText().toString().contains("30s"),
                "Returning focus must restart the full wait");
        });
        SystemClock.sleep(AppFriction.HARD_WAIT_MS + 300);
        test.runOnMainSync(() -> {
            require(button(gate[0]).isEnabled(), "Full wait must enable Open");
            require(opened[0] == 0, "Countdown must not open an app automatically");
            button(gate[0]).performClick();
            button(gate[0]).performClick();
            require(opened[0] == 1, "Open must launch exactly once");
            gate[0] = new LaunchGate(activity, "Chat agent", AppFriction.Level.SLIGHT,
                () -> opened[0]++, () -> {});
            activity.setContentView(gate[0]);
        });
        test.waitForIdleSync();
        test.runOnMainSync(() -> {
            button(gate[0]).performClick();
            require(opened[0] == 1, "A tap must not bypass the hold");
            touch(button(gate[0]), MotionEvent.ACTION_DOWN, 10);
        });
        SystemClock.sleep(500);
        test.runOnMainSync(() -> touch(button(gate[0]), MotionEvent.ACTION_UP, 10));
        SystemClock.sleep(AppFriction.SLIGHT_HOLD_MS + 100);
        test.runOnMainSync(() -> {
            require(opened[0] == 1, "Early release must cancel the pending launch");
            touch(button(gate[0]), MotionEvent.ACTION_DOWN, 10);
            touch(button(gate[0]), MotionEvent.ACTION_MOVE, -1);
        });
        SystemClock.sleep(AppFriction.SLIGHT_HOLD_MS + 100);
        test.runOnMainSync(() -> {
            require(opened[0] == 1, "Dragging outside must cancel the hold");
            touch(button(gate[0]), MotionEvent.ACTION_DOWN, 10);
            touch(button(gate[0]), MotionEvent.ACTION_CANCEL, 10);
            require(!button(gate[0]).isPressed(), "Cancelled touch must release button");
            touch(button(gate[0]), MotionEvent.ACTION_DOWN, 10);
        });
        SystemClock.sleep(AppFriction.SLIGHT_HOLD_MS + 200);
        test.runOnMainSync(() -> {
            require(opened[0] == 2, "A full hold must open exactly once");
            button(gate[0]).performClick();
            require(opened[0] == 2, "A completed hold must not launch twice");
            gate[0] = new LaunchGate(activity, "Cancelled attempt", AppFriction.Level.SLIGHT,
                () -> opened[0]++, () -> {});
            activity.setContentView(gate[0]);
        });
        test.waitForIdleSync();
        test.runOnMainSync(() -> {
            touch(button(gate[0]), MotionEvent.ACTION_DOWN, 10);
            activity.setContentView(new View(activity));
        });
        SystemClock.sleep(AppFriction.SLIGHT_HOLD_MS + 100);
        test.runOnMainSync(() -> {
            require(opened[0] == 2, "Leaving the view must cancel a pending launch");
            activity.onBackPressed();
        });
    }

    private static Button button(LaunchGate gate) { return (Button) gate.getChildAt(2); }

    private static void touch(View view, int action, float x) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, 10, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
