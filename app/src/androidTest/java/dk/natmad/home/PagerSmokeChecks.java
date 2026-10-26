package dk.natmad.home;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import java.util.function.BooleanSupplier;

/** Checks the in-flight geometry as well as the settled page, using real view dispatch/animation. */
final class PagerSmokeChecks {
    static void run(Instrumentation test, ViewGroup root) {
        SectionPager pager = pager(root);
        int[] navigationPosition = new int[2];
        test.runOnMainSync(() -> {
            SmallGoalsSmokeChecks.find(root, "Everything else").getLocationOnScreen(navigationPosition);
            int width = pager.getWidth();
            float y = pager.getHeight() * 0.8f;
            long down = SystemClock.uptimeMillis();
            touch(pager, down, 0, MotionEvent.ACTION_DOWN, width * 0.85f, y);
            touch(pager, down, 200, MotionEvent.ACTION_MOVE, width * 0.6f, y);
            require(Math.abs(pager.getScrollX() - width * 0.25f) < 2, "Content must follow finger before release");
            touch(pager, down, 400, MotionEvent.ACTION_MOVE, width * 0.45f, y);
            require(Math.abs(pager.getScrollX() - width * 0.4f) < 2, "Content must continue tracking the gesture");
            require(pager.getChildAt(0).getVisibility() == View.VISIBLE
                && pager.getChildAt(1).getVisibility() == View.VISIBLE, "Both pages must be drawn during a swipe");
            int[] after = new int[2];
            SmallGoalsSmokeChecks.find(root, "Everything else").getLocationOnScreen(after);
            require(after[0] == navigationPosition[0] && after[1] == navigationPosition[1],
                "Swiping the bottom section must not move the surrounding layout");
            touch(pager, down, 450, MotionEvent.ACTION_CANCEL, width * 0.45f, y);
            require(pager.getScrollX() > 0, "Cancellation should animate back, not jump");
        });
        await(test, () -> pager.getScrollX() == 0 && pager.getChildAt(1).getVisibility() == View.INVISIBLE,
            "Cancelled swipe must settle back on music");
        test.runOnMainSync(() -> drag(pager, 0.8f, 0.6f, 600));
        await(test, () -> pager.getScrollX() == 0 && pager.getChildAt(1).getVisibility() == View.INVISIBLE,
            "Short slow drag must return to its page");
        test.runOnMainSync(() -> drag(pager, 0.8f, 0.55f, 45));
        await(test, () -> pager.getScrollX() == pager.getWidth() && pager.getChildAt(0).getVisibility() == View.INVISIBLE,
            "Short fast fling must advance to goals");
        test.runOnMainSync(() -> {
            require(SmallGoalsSmokeChecks.find(root, "Small goal") == null, "Input must not be permanent");
            SmallGoalsSmokeChecks.find(root, "Add goal").performClick();
            EditText input = (EditText) SmallGoalsSmokeChecks.find(root, "Small goal");
            input.setText("Keep this draft across swipes");
            require(pager.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null),
                "Music must be accessible without visible tabs");
        });
        await(test, () -> pager.getScrollX() == 0 && pager.getChildAt(1).getVisibility() == View.INVISIBLE,
            "Accessible scroll must settle on music");
        test.runOnMainSync(() -> SmallGoalsSmokeChecks.swipe(root, true));
        await(test, () -> pager.getScrollX() == pager.getWidth() && pager.getChildAt(0).getVisibility() == View.INVISIBLE,
            "Full swipe must settle on goals");
        test.runOnMainSync(() -> {
            EditText input = (EditText) SmallGoalsSmokeChecks.find(root, "Small goal");
            require(input.getText().toString().equals("Keep this draft across swipes"), "Swipe discarded draft");
            input.setText("");
            SmallGoalsSmokeChecks.find(root, "Cancel").performClick();
            SmallGoalsSmokeChecks.swipe(root, false);
        });
        await(test, () -> pager.getScrollX() == 0 && pager.getChildAt(1).getVisibility() == View.INVISIBLE,
            "Swipe right must restore music controls");
    }

    private static void drag(SectionPager pager, float from, float to, int duration) {
        long down = SystemClock.uptimeMillis();
        float y = pager.getHeight() * 0.8f;
        touch(pager, down, 0, MotionEvent.ACTION_DOWN, pager.getWidth() * from, y);
        for (int i = 1; i <= 4; i++) {
            touch(pager, down, duration * i / 4, i == 4 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE,
                pager.getWidth() * (from + (to - from) * i / 4), y);
        }
    }

    private static void touch(View view, long down, int elapsed, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, down + elapsed, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    private static SectionPager pager(View view) {
        if (view instanceof SectionPager) return (SectionPager) view;
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                SectionPager found = pager(((ViewGroup) view).getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void await(Instrumentation test, BooleanSupplier condition, String message) {
        long deadline = SystemClock.uptimeMillis() + 3000;
        boolean[] passed = {false};
        do {
            test.runOnMainSync(() -> passed[0] = condition.getAsBoolean());
            if (passed[0]) return;
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
