package dk.natmad.home;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A foreground-only opening attempt. Detaching or losing focus discards all progress. */
final class LaunchGate extends LinearLayout {
    private static final String WAIT_INSTRUCTION = "Wait 30 seconds before opening.";
    private static final String HOLD_INSTRUCTION = "Hold for 3 seconds to open. Release to cancel.";
    private static final String ACCESSIBLE_INSTRUCTION = "Activate, wait 3 seconds, then activate again to open.";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AppFriction.Level level;
    private final Runnable open;
    private final long duration;
    private final TextView status;
    private final Button button;
    private long startedAt;
    private boolean running;
    private boolean ready;
    private boolean completed;
    private boolean holding;
    private final Runnable tick = this::update;

    LaunchGate(Context context, String label, AppFriction.Level level, Runnable open, Runnable cancel) {
        super(context);
        this.level = level;
        this.open = open;
        duration = level == AppFriction.Level.HARD ? AppFriction.HARD_WAIT_MS : AppFriction.SLIGHT_HOLD_MS;
        setOrientation(VERTICAL);
        addView(text(label, 30));
        status = text("", 18);
        // Measure every possible message at the current width/font scale. Invisible
        // copies reserve the space without clipping instructions or moving the target.
        FrameLayout statusArea = new FrameLayout(context);
        String[] messages = level == AppFriction.Level.HARD
            ? new String[] {WAIT_INSTRUCTION, "Wait · 30s", "Ready when you are."}
            : new String[] {HOLD_INSTRUCTION, ACCESSIBLE_INSTRUCTION,
                "Keep holding · 3s", "Wait · 3s", "Ready when you are."};
        for (String message : messages) {
            TextView reserve = text(message, 18);
            reserve.setVisibility(INVISIBLE);
            reserve.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            statusArea.addView(reserve, new FrameLayout.LayoutParams(-1, -2));
        }
        statusArea.addView(status, new FrameLayout.LayoutParams(-1, -2));
        addView(statusArea, new LayoutParams(-1, -2));
        button = new Button(context);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(225, 225, 225));
        button.setTextSize(20);
        addView(button, new LayoutParams(-1, -2));
        button.setOnClickListener(view -> {
            if (!isAttachedToWindow() || !hasWindowFocus() || completed) return;
            if (ready) {
                completed = true;
                handler.removeCallbacks(tick);
                open.run();
            } else if (level == AppFriction.Level.SLIGHT && touchExploration()) {
                // TalkBack uses activation gestures rather than a continuous physical press.
                // Require the same delay, then a separate activation to open.
                if (!running) start(false);
            }
        });
        if (level == AppFriction.Level.SLIGHT) {
            button.setOnTouchListener((view, event) -> {
                if (touchExploration()) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        start(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (event.getX() < 0 || event.getX() >= view.getWidth()
                                || event.getY() < 0 || event.getY() >= view.getHeight()) reset();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_POINTER_DOWN:
                        reset();
                        return true;
                    default: return true;
                }
            });
            button.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_DPAD_CENTER
                        && keyCode != KeyEvent.KEYCODE_SPACE) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) start(true);
                else if (event.getAction() == KeyEvent.ACTION_UP) reset();
                return true;
            });
            button.setOnFocusChangeListener((view, focused) -> { if (!focused) reset(); });
        }
        Button back = new Button(context);
        back.setText("Go back");
        back.setAllCaps(false);
        back.setOnClickListener(view -> cancel.run());
        addView(back, new LayoutParams(-1, -2));
        reset();
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextColor(Color.rgb(200, 200, 200));
        view.setTextSize(size);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        view.setPadding(0, padding, 0, padding);
        return view;
    }

    private boolean touchExploration() {
        return ((AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE))
            .isTouchExplorationEnabled();
    }

    private void reset() {
        handler.removeCallbacks(tick);
        running = false;
        ready = false;
        holding = false;
        button.setPressed(false);
        button.setEnabled(level == AppFriction.Level.SLIGHT);
        button.setText(level == AppFriction.Level.HARD ? "Open" : "Hold to open");
        status.setText(level == AppFriction.Level.HARD ? WAIT_INSTRUCTION
            : touchExploration() ? ACCESSIBLE_INSTRUCTION : HOLD_INSTRUCTION);
        if (level == AppFriction.Level.SLIGHT && touchExploration()) button.setText("Continue");
    }

    private void start(boolean hold) {
        if (completed || !isAttachedToWindow() || !hasWindowFocus()) return;
        reset();
        holding = hold;
        button.setPressed(hold);
        running = true;
        startedAt = SystemClock.uptimeMillis();
        update();
    }

    private void update() {
        if (!running || completed || !isAttachedToWindow() || !hasWindowFocus()) return;
        long remaining = duration - (SystemClock.uptimeMillis() - startedAt);
        if (remaining <= 0) {
            running = false;
            ready = true;
            button.setEnabled(true);
            button.setText("Open");
            status.setText("Ready when you are.");
            if (holding) button.performClick();
        } else {
            status.setText((holding ? "Keep holding · " : "Wait · ") + ((remaining + 999) / 1000) + "s");
            handler.postDelayed(tick, Math.min(100, remaining));
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (level == AppFriction.Level.HARD && hasWindowFocus()) start(false);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        reset();
        if (focused && level == AppFriction.Level.HARD) start(false);
    }

    @Override protected void onDetachedFromWindow() {
        reset();
        completed = true;
        super.onDetachedFromWindow();
    }
}
