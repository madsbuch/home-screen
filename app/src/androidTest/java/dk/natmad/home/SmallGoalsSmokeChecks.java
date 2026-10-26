package dk.natmad.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import java.time.LocalDate;

final class SmallGoalsSmokeChecks {
    static void run(Instrumentation test, Activity activity) {
        SmallGoals store = new SmallGoals(activity);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // A crashed instrumentation process can leave its reserved fixtures behind.
        for (SmallGoals.Goal goal : store.read(LocalDate.now())) {
            if (goal.title.endsWith("— smoke test")) store.delete(LocalDate.now(), goal.id);
        }
        for (SmallGoals.Goal goal : store.read(yesterday)) {
            if (goal.title.equals("Yesterday fixture")) store.delete(yesterday, goal.id);
        }
        store.add(yesterday, "Yesterday fixture");
        SmallGoals.Goal old = store.read(yesterday).stream()
            .filter(goal -> goal.title.equals("Yesterday fixture")).findFirst().get();
        String[] savedId = {null};
        try {
            test.runOnMainSync(() -> {
                activity.onBackPressed();
                ViewGroup root = activity.findViewById(android.R.id.content);
                require(find(root, "Små Mål") != null, "Goals section missing");
                require(find(root, "Music") == null, "No music must leave only small goals");
                require(find(root, "Yesterday fixture") == null, "Yesterday's goals leaked into today");
                find(root, "+ Add goal").performClick();
                HomeSections sections = sections(root);
                AlertDialog dialog = editor(sections);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                require(dialog.isShowing(), "Blank goal must keep editor open");
                EditText input = (EditText) find(dialog.getWindow().getDecorView(), "Small goal");
                input.setText("  Go for a run — smoke test  ");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                CheckBox goal = (CheckBox) find(root, "Go for a run — smoke test");
                require(goal != null && !goal.isChecked(), "New goal missing or already completed");
                goal.performClick();
                SmallGoals.Goal saved = new SmallGoals(activity).read(LocalDate.now()).stream()
                    .filter(item -> item.title.equals("Go for a run — smoke test")).findFirst().get();
                savedId[0] = saved.id;
                require(saved.done, "Completion did not persist");
                activity.onBackPressed();
                goal = (CheckBox) find(root, "Go for a run — smoke test");
                require(goal != null && goal.isChecked(), "Rebuilding home lost completion");
                goal.performClick();
                require(!new SmallGoals(activity).read(LocalDate.now()).stream()
                    .filter(item -> item.id.equals(saved.id)).findFirst().get().done, "Unchecking did not persist");
                find(root, "Edit goal: Go for a run — smoke test").performClick();
                dialog = editor(sections(root));
                input = (EditText) find(dialog.getWindow().getDecorView(), "Small goal");
                input.setText("Go for a swim — smoke test");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                require(find(root, "Go for a swim — smoke test") != null, "Editing did not update goal");
                find(root, "Edit goal: Go for a swim — smoke test").performClick();
                editor(sections(root)).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            });
            test.waitForIdleSync();
            test.runOnMainSync(() -> {
                require(store.read(LocalDate.now()).stream().noneMatch(item -> item.id.equals(savedId[0])),
                    "Deleting did not persist");
                require(store.read(yesterday).stream().anyMatch(item -> item.id.equals(old.id)),
                    "Editing today's goals changed yesterday");
            });
        } finally {
            test.runOnMainSync(() -> {
                store.delete(yesterday, old.id);
                for (SmallGoals.Goal goal : store.read(LocalDate.now())) {
                    if (goal.title.endsWith("— smoke test")) store.delete(LocalDate.now(), goal.id);
                }
                activity.onBackPressed();
            });
        }
        test.waitForIdleSync();
        test.runOnMainSync(() -> {
            ViewGroup root = activity.findViewById(android.R.id.content);
            swipe(root, false);
            require(find(root, "+ Add goal") != null && find(root, "Music") == null,
                "Swiping without music must stay on goals");
        });
    }

    static void swipe(View root, boolean left) {
        HomeSections section = sections(root);
        // Cross a child view, exercising interception instead of directly selecting a tab.
        float start = section.getWidth() * (left ? 0.85f : 0.15f);
        float end = section.getWidth() - start;
        float y = section.getHeight() * 0.7f;
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i <= 8; i++) {
            MotionEvent event = MotionEvent.obtain(down, down + i * 30,
                i == 0 ? MotionEvent.ACTION_DOWN : i == 8 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE,
                start + (end - start) * i / 8, y, 0);
            section.dispatchTouchEvent(event);
            event.recycle();
        }
    }

    static View find(View root, String label) {
        if (root.getVisibility() != View.VISIBLE) return null;
        if (label.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof TextView && label.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View result = find(((ViewGroup) root).getChildAt(i), label);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static HomeSections sections(View view) {
        if (view instanceof HomeSections) return (HomeSections) view;
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                HomeSections result = sections(((ViewGroup) view).getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static AlertDialog editor(HomeSections sections) {
        try {
            java.lang.reflect.Field field = HomeSections.class.getDeclaredField("editor");
            field.setAccessible(true);
            return (AlertDialog) field.get(sections);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
