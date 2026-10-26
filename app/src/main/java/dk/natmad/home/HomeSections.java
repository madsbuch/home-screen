package dk.natmad.home;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.List;
import java.util.function.IntConsumer;

/** Two bottom sections, reachable with horizontal swipes or accessible tab buttons. */
final class HomeSections extends LinearLayout {
    private static final int WHITE = 0xffe1e1e1;
    private static final int MUTED = 0xff999999;
    private final SmallGoals store;
    private final IntConsumer onSelection;
    private final ScrollView musicPage;
    private final LinearLayout goalsPage;
    private boolean musicAvailable;
    private final Button musicTab;
    private final Button goalsTab;
    private final SwipeArea pages;
    private int selected;
    private int preferredSection;
    private LocalDate date = LocalDate.now();
    private AlertDialog editor;
    private final Runnable dateCheck = new Runnable() {
        @Override public void run() {
            refreshDate();
            postDelayed(this, 1000);
        }
    };

    HomeSections(Context context, int initialSection, MediaControls media, IntConsumer onSelection) {
        super(context);
        this.store = new SmallGoals(context);
        this.onSelection = onSelection;
        this.preferredSection = initialSection;
        setOrientation(VERTICAL);
        View divider = new View(context);
        divider.setBackgroundColor(0xff333333);
        addView(divider, new LayoutParams(-1, dp(1)));
        LinearLayout tabs = new LinearLayout(context);
        musicTab = button("Music", () -> choose(0));
        goalsTab = button("Små Mål", () -> choose(1));
        tabs.addView(musicTab, new LayoutParams(0, -2, 1));
        tabs.addView(goalsTab, new LayoutParams(0, -2, 1));
        addView(tabs);
        pages = new SwipeArea(context);
        addView(pages, new LayoutParams(-1, 0, 1));
        musicPage = new ScrollView(context);
        musicPage.addView(media);
        pages.addView(musicPage, new FrameLayout.LayoutParams(-1, -1));
        goalsPage = new LinearLayout(context);
        goalsPage.setOrientation(VERTICAL);
        pages.addView(goalsPage, new FrameLayout.LayoutParams(-1, -1));
        musicTab.setVisibility(GONE);
        select(initialSection);
        media.setAvailabilityListener(available -> {
            boolean changed = musicAvailable != available;
            musicAvailable = available;
            musicTab.setVisibility(available ? VISIBLE : GONE);
            if (changed) select(available ? preferredSection : 1);
        });
    }

    private void choose(int section) {
        select(section);
        preferredSection = selected;
        onSelection.accept(selected);
    }

    private void select(int section) {
        selected = !musicAvailable || section == 1 ? 1 : 0;
        musicTab.setSelected(selected == 0);
        goalsTab.setSelected(selected == 1);
        musicTab.setTextColor(selected == 0 ? WHITE : MUTED);
        goalsTab.setTextColor(selected == 1 ? WHITE : MUTED);
        musicTab.setPaintFlags(selected == 0 ? Paint.UNDERLINE_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG : Paint.ANTI_ALIAS_FLAG);
        goalsTab.setPaintFlags(selected == 1 ? Paint.UNDERLINE_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG : Paint.ANTI_ALIAS_FLAG);
        render();
    }

    private void render() {
        musicPage.setVisibility(selected == 0 ? VISIBLE : GONE);
        goalsPage.setVisibility(selected == 1 ? VISIBLE : GONE);
        if (selected == 0) return;
        goalsPage.removeAllViews();
        LinearLayout content = goalsPage;
        List<SmallGoals.Goal> goals = store.read(date);
        long completed = goals.stream().filter(goal -> goal.done).count();
        LinearLayout heading = new LinearLayout(getContext());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(copy("Today · " + completed + "/" + goals.size(), 14, MUTED), new LayoutParams(0, -2, 1));
        heading.addView(button("+ Add goal", () -> edit(null)));
        content.addView(heading);
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(true);
        LinearLayout rows = new LinearLayout(getContext());
        rows.setOrientation(VERTICAL);
        scroll.addView(rows);
        content.addView(scroll, new LayoutParams(-1, 0, 1));
        if (goals.isEmpty()) {
            rows.addView(copy("What’s one small thing for today?", 16, WHITE));
            rows.addView(copy("Go for a run. Go for a swim. Finish task A.", 14, MUTED));
        }
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
                render();
            });
            row.addView(check, new LayoutParams(0, -2, 1));
            Button edit = button("Edit", () -> edit(goal));
            edit.setTextSize(12);
            edit.setContentDescription("Edit goal: " + goal.title);
            row.addView(edit, new LayoutParams(dp(56), -2));
            rows.addView(row);
        }
    }

    private void edit(SmallGoals.Goal goal) {
        LocalDate editingDate = date;
        refreshDate();
        EditText input = new EditText(getContext());
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("e.g. Go for a run");
        input.setContentDescription("Small goal");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setPadding(dp(24), dp(12), dp(24), dp(12));
        if (goal != null) { input.setText(goal.title); input.setSelection(input.length()); }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
            .setTitle(goal == null ? "A small goal for today" : "Edit small goal")
            .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null);
        if (goal != null) builder.setNeutralButton("Delete", (dialog, which) -> {
            store.delete(editingDate, goal.id);
            refreshDate();
            render();
        });
        editor = builder.create();
        editor.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        input.requestFocus();
        editor.show();
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String title = input.getText().toString().trim();
            if (title.isEmpty()) { input.setError("Give your small goal a name"); return; }
            if (goal == null) store.add(LocalDate.now(), title);
            else store.update(editingDate, goal.id, title, goal.done);
            refreshDate();
            render();
            editor.dismiss();
        });
    }

    private void refreshDate() {
        LocalDate today = LocalDate.now();
        if (!today.equals(date)) { date = today; render(); }
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
        if (editor != null) editor.dismiss();
        super.onDetachedFromWindow();
    }

    private TextView copy(String text, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
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

    /** Intercept only horizontal gestures so goal rows remain tappable and vertically scrollable. */
    private final class SwipeArea extends FrameLayout {
        private float startX;
        private float startY;
        private boolean horizontal;
        private boolean vertical;
        private final int slop;

        SwipeArea(Context context) { super(context); slop = ViewConfiguration.get(context).getScaledTouchSlop(); }

        @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
            // A nested ScrollView asks immediately; retain the ability to identify horizontal swipes.
            if (vertical || !disallow) super.requestDisallowInterceptTouchEvent(disallow);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = event.getX(); startY = event.getY(); horizontal = false; vertical = false;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !vertical) {
                float dx = Math.abs(event.getX() - startX);
                float dy = Math.abs(event.getY() - startY);
                if (dy > slop && dy > dx) vertical = true;
                else if (dx > slop && dx > dy * 1.5f) horizontal = true;
            }
            return horizontal;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - startX;
                if (musicAvailable && !vertical && Math.abs(dx) >= dp(48) && Math.abs(dx) > Math.abs(event.getY() - startY) * 1.5f) {
                    choose(dx < 0 ? 1 : 0);
                }
                horizontal = false;
                performClick();
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) horizontal = false;
            return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
