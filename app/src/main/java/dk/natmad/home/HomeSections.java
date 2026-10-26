package dk.natmad.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.List;
import java.util.function.IntConsumer;

/** The bottom music/goals surface; goal entry and editing stay inside the goals page. */
final class HomeSections extends LinearLayout {
    private static final int WHITE = 0xffe1e1e1;
    private static final int MUTED = 0xff999999;
    private final SmallGoals store;
    private final SectionPager pages;
    private final TextView count;
    private final EditText input;
    private final Button submit;
    private final LinearLayout editActions;
    private final LinearLayout rows;
    private final ScrollView goalScroll;
    private int preferredSection;
    private boolean musicAvailable;
    private LocalDate date = LocalDate.now();
    private String editingId;
    private String draftBeforeEdit = "";
    private final Runnable dateCheck = new Runnable() {
        @Override public void run() {
            refreshDate();
            postDelayed(this, 1000);
        }
    };

    HomeSections(Context context, int initialSection, Bundle state, MediaControls media, IntConsumer onSelection) {
        super(context);
        store = new SmallGoals(context);
        preferredSection = initialSection;
        setOrientation(VERTICAL);
        setFocusableInTouchMode(true);
        View divider = new View(context);
        divider.setBackgroundColor(0xff333333);
        addView(divider, new LayoutParams(-1, dp(1)));
        pages = new SectionPager(context, section -> {
            preferredSection = section;
            onSelection.accept(section);
            if (section == 0) dismissKeyboard();
        });
        addView(pages, new LayoutParams(-1, 0, 1));
        ScrollView musicPage = new ScrollView(context);
        musicPage.addView(media);
        pages.addView(musicPage);
        LinearLayout goalsPage = new LinearLayout(context);
        goalsPage.setOrientation(VERTICAL);
        pages.addView(goalsPage);

        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = copy("Små Mål", 18, WHITE);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        heading.addView(title, new LayoutParams(0, dp(44), 1));
        count = copy("", 13, MUTED);
        heading.addView(count);
        goalsPage.addView(heading);

        LinearLayout entry = new LinearLayout(context);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(context);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        input.setHint("A small goal for today");
        input.setContentDescription("Small goal");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setMinHeight(dp(48));
        input.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        entry.addView(input, new LayoutParams(0, -2, 1));
        submit = button("Add", this::saveGoal);
        entry.addView(submit, new LayoutParams(dp(64), dp(48)));
        goalsPage.addView(entry);
        editActions = new LinearLayout(context);
        editActions.addView(button("Cancel", this::finishEdit), new LayoutParams(0, dp(48), 1));
        editActions.addView(button("Delete goal", () -> {
            if (editingId != null) store.delete(date, editingId);
            finishEdit();
            renderGoals();
        }), new LayoutParams(0, dp(48), 1));
        goalsPage.addView(editActions);
        goalScroll = new ScrollView(context);
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        goalScroll.addView(rows);
        goalsPage.addView(goalScroll, new LayoutParams(-1, 0, 1));

        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                submit.setEnabled(!text.toString().trim().isEmpty());
                submit.setAlpha(submit.isEnabled() ? 1f : 0.4f);
            }
            public void afterTextChanged(Editable text) {}
        });
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            saveGoal();
            return true;
        });
        restoreEditorState(state);
        renderGoals();
        pages.setMusicAvailable(false, preferredSection);
        media.setAvailabilityListener(available -> {
            if (musicAvailable == available) return;
            musicAvailable = available;
            // Do not take an in-progress goal draft away when a player starts.
            if (available && input.hasFocus()) { preferredSection = 1; onSelection.accept(1); }
            pages.setMusicAvailable(available, preferredSection);
        });
    }

    private void renderGoals() {
        int scrollY = goalScroll.getScrollY();
        rows.removeAllViews();
        List<SmallGoals.Goal> goals = store.read(date);
        long completed = goals.stream().filter(goal -> goal.done).count();
        count.setText("Today · " + completed + "/" + goals.size());
        if (goals.isEmpty()) rows.addView(copy("Go for a run. Go for a swim. Finish task A.", 14, MUTED));
        for (SmallGoals.Goal goal : goals) {
            LocalDate goalDate = date;
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox check = new CheckBox(getContext());
            check.setText(goal.title);
            check.setTextSize(16);
            check.setTextColor(goal.done ? MUTED : WHITE);
            check.setButtonTintList(ColorStateList.valueOf(MUTED));
            check.setMinHeight(dp(48));
            check.setChecked(goal.done);
            if (goal.done) check.setPaintFlags(check.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            check.setOnCheckedChangeListener((view, done) -> {
                store.update(goalDate, goal.id, goal.title, done);
                renderGoals();
            });
            row.addView(check, new LayoutParams(0, -2, 1));
            Button edit = button("Edit", () -> edit(goal));
            edit.setTextSize(12);
            edit.setContentDescription("Edit goal: " + goal.title);
            row.addView(edit, new LayoutParams(dp(56), -2));
            rows.addView(row);
        }
        goalScroll.post(() -> goalScroll.scrollTo(0, scrollY));
    }

    private void saveGoal() {
        String title = input.getText().toString().trim();
        if (title.isEmpty()) return;
        if (editingId == null) {
            store.add(LocalDate.now(), title);
            input.setText("");
        } else {
            for (SmallGoals.Goal goal : store.read(date)) {
                if (goal.id.equals(editingId)) store.update(date, editingId, title, goal.done);
            }
            finishEdit();
        }
        refreshDate();
        renderGoals();
        goalScroll.post(() -> goalScroll.fullScroll(FOCUS_DOWN));
    }

    private void edit(SmallGoals.Goal goal) {
        if (editingId == null) draftBeforeEdit = input.getText().toString();
        editingId = goal.id;
        input.setText(goal.title);
        updateEditor();
        input.setSelection(input.length());
        input.requestFocus();
        input.post(() -> {
            if (input.isAttachedToWindow() && input.hasFocus())
                getContext().getSystemService(InputMethodManager.class).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void finishEdit() {
        editingId = null;
        input.setText(draftBeforeEdit);
        draftBeforeEdit = "";
        updateEditor();
        dismissKeyboard();
    }

    private void updateEditor() {
        submit.setText(editingId == null ? "Add" : "Save");
        input.setHint(editingId == null ? "A small goal for today" : "Edit small goal");
        editActions.setVisibility(editingId == null ? GONE : VISIBLE);
    }

    private void dismissKeyboard() {
        input.clearFocus();
        requestFocus();
        getContext().getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    Bundle saveEditorState() {
        Bundle state = new Bundle();
        state.putString("text", input.getText().toString());
        state.putString("editing_id", editingId);
        state.putString("date", date.toString());
        state.putString("draft", draftBeforeEdit);
        return state;
    }

    private void restoreEditorState(Bundle state) {
        String text = "";
        if (state != null) {
            text = state.getString("text", "");
            String id = state.getString("editing_id");
            if (id != null) {
                if (date.toString().equals(state.getString("date"))
                        && store.read(date).stream().anyMatch(goal -> goal.id.equals(id))) {
                    editingId = id;
                    draftBeforeEdit = state.getString("draft", "");
                } else text = state.getString("draft", "");
            }
        }
        input.setText(text);
        updateEditor();
    }

    private void refreshDate() {
        LocalDate today = LocalDate.now();
        if (!today.equals(date)) {
            if (editingId != null) finishEdit();
            date = today;
            renderGoals();
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int compactHeight = Math.min(dp(240), Math.round(getResources().getDisplayMetrics().heightPixels * 0.32f));
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(
            Math.min(compactHeight, MeasureSpec.getSize(heightSpec)), MeasureSpec.EXACTLY));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(dateCheck);
        post(dateCheck);
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        removeCallbacks(dateCheck);
        if (visibility == VISIBLE) post(dateCheck);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(dateCheck);
        super.onDetachedFromWindow();
    }

    private TextView copy(String text, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private Button button(String text, Runnable action) {
        Button view = new Button(getContext());
        view.setText(text);
        view.setAllCaps(false);
        view.setTextSize(15);
        view.setTextColor(WHITE);
        view.setMinHeight(dp(48));
        view.setMinimumWidth(0);
        view.setMinWidth(0);
        view.setPadding(dp(8), 0, dp(8), 0);
        android.util.TypedValue background = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
        view.setBackgroundResource(background.resourceId);
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
