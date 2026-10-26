package dk.natmad.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;
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
                require(!(find(root, "Små Mål") instanceof android.widget.Button), "Section tabs must be removed");
                require(find(root, "Small goal") == null, "New-goal input must be hidden until Add is tapped");
                require(!hasExtraLabels(root), "Goals must not show a header, counter, or sample text");
                find(root, "Add goal").performClick();
                EditText input = (EditText) find(root, "Small goal");
                require(input != null, "Add must reveal the inline editor");
                input.setText("  ");
                require(!find(root, "Add").isEnabled(), "Blank goals must not be submitted");
                input.setText("  Go for a run — smoke test  ");
                input.onEditorAction(EditorInfo.IME_ACTION_DONE);
                require(find(root, "Small goal") == null, "Saving must collapse the editor");
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
                find(root, "Add goal").performClick();
                input = (EditText) find(root, "Small goal");
                input.setText("Unfinished draft");
                activity.onBackPressed();
                input = (EditText) find(root, "Small goal");
                require(input.getText().toString().equals("Unfinished draft"), "Rebuilding home lost draft");
                find(root, "Go for a run — smoke test").performLongClick();
                input.setText("Cancelled edit");
                find(root, "Cancel").performClick();
                require(input.getText().toString().equals("Unfinished draft"), "Cancel must restore new-goal draft");
                require(find(root, "Go for a run — smoke test") != null, "Cancel changed saved goal");
                find(root, "Go for a run — smoke test").performLongClick();
                input.setText("Go for a swim — smoke test");
                activity.onBackPressed();
                input = (EditText) find(root, "Small goal");
                require(input.getText().toString().equals("Go for a swim — smoke test")
                    && find(root, "Save") != null, "Rebuilding home lost inline edit");
                find(root, "Save").performClick();
                require(find(root, "Go for a swim — smoke test") != null, "Editing did not update goal");
                require(input.getText().toString().equals("Unfinished draft"), "Save must restore the draft");
                find(root, "Go for a swim — smoke test").performLongClick();
                find(root, "Delete goal").performClick();
                input.setText("");
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
            require(find(root, "Add goal") != null && find(root, "Music") == null,
                "Swiping without music must stay on goals");
            require(sections(root).getHeight() <= Math.round(56 * activity.getResources().getDisplayMetrics().density),
                "Empty goals section must collapse to a single compact row");
        });
    }

    static void swipe(View root, boolean left) {
        HomeSections section = sections(root);
        // Cross a child view, exercising interception instead of directly selecting a tab.
        float start = section.getWidth() * (left ? 0.85f : 0.15f);
        float end = section.getWidth() - start;
        float y = section.getHeight() * 0.25f;
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

    private static boolean hasExtraLabels(View view) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof TextView) {
            String text = ((TextView) view).getText().toString();
            if (text.equals("Små Mål") || text.startsWith("Today ·") || text.contains("Finish task A")) return true;
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                if (hasExtraLabels(((ViewGroup) view).getChildAt(i))) return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
