package dk.natmad.home;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.OverScroller;
import java.util.function.IntConsumer;

/** A two-page viewport: content follows the finger, then settles by distance or fling velocity. */
final class SectionPager extends ViewGroup {
    private final OverScroller scroller;
    private final IntConsumer onSelection;
    private final int slop;
    private final int flingVelocity;
    private VelocityTracker velocity;
    private boolean hasMusic;
    private boolean dragging;
    private boolean vertical;
    private boolean ignoreGesture;
    private boolean settling;
    private float startX;
    private float startY;
    private int startScroll;
    private int selected = 1;

    SectionPager(Context context, IntConsumer onSelection) {
        super(context);
        this.onSelection = onSelection;
        scroller = new OverScroller(context);
        ViewConfiguration config = ViewConfiguration.get(context);
        slop = config.getScaledTouchSlop();
        flingVelocity = Math.max(config.getScaledMinimumFlingVelocity(), Math.round(500 * getResources().getDisplayMetrics().density));
        setClipChildren(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setMusicAvailable(boolean available, int preferredSection) {
        scroller.abortAnimation();
        settling = dragging = false;
        ignoreGesture = true;
        recycleVelocity();
        hasMusic = available;
        selected = available ? preferredSection : 1;
        updateVisibility();
        scrollTo(targetScroll(), 0);
        requestLayout();
    }

    private int targetScroll() { return hasMusic ? selected * getWidth() : 0; }

    private void updateVisibility() {
        if (getChildCount() != 2) return;
        getChildAt(0).setVisibility(!hasMusic ? GONE : dragging || settling || selected == 0 ? VISIBLE : INVISIBLE);
        getChildAt(1).setVisibility(dragging || settling || selected == 1 ? VISIBLE : INVISIBLE);
        for (int i = 0; i < 2; i++) {
            getChildAt(i).setImportantForAccessibility(i == selected
                ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        setContentDescription(hasMusic
            ? selected == 0 ? "Music. Swipe left for Små Mål" : "Små Mål. Swipe right for music"
            : "Små Mål");
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        for (int i = 0; i < getChildCount(); i++) {
            int x = hasMusic ? i * width : 0;
            getChildAt(i).layout(x, 0, x + width, bottom - top);
        }
        if (!dragging && !settling) scrollTo(targetScroll(), 0);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth) {
            scroller.abortAnimation();
            settling = dragging = false;
            ignoreGesture = true;
            updateVisibility();
            scrollTo(targetScroll(), 0);
        }
    }

    @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
        // A vertical ScrollView asks on DOWN, before its direction is known.
        if (vertical || ignoreGesture || !disallow) super.requestDisallowInterceptTouchEvent(disallow);
    }

    private void begin(MotionEvent event) {
        scroller.abortAnimation();
        settling = dragging = vertical = false;
        startX = event.getX();
        startY = event.getY();
        startScroll = getScrollX();
        ignoreGesture = !hasMusic || touchesEditor(this, event);
        recycleVelocity();
        if (!ignoreGesture) {
            velocity = VelocityTracker.obtain();
            velocity.addMovement(event);
        }
    }

    private boolean touchesEditor(View view, MotionEvent event) {
        if (view.getVisibility() != VISIBLE) return false;
        if (view instanceof EditText) {
            Rect bounds = new Rect();
            return view.getGlobalVisibleRect(bounds) && bounds.contains((int) event.getRawX(), (int) event.getRawY());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (touchesEditor(group.getChildAt(i), event)) return true;
            }
        }
        return false;
    }

    private void detectDrag(MotionEvent event) {
        if (ignoreGesture || vertical || dragging) return;
        float dx = Math.abs(event.getX() - startX);
        float dy = Math.abs(event.getY() - startY);
        if (dy > slop && dy >= dx) vertical = true;
        else if (dx > slop && dx > dy * 1.5f) {
            dragging = true;
            updateVisibility();
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) begin(event);
        else if (action == MotionEvent.ACTION_MOVE) {
            if (velocity != null) velocity.addMovement(event);
            detectDrag(event);
            if (dragging) followFinger(event);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!ignoreGesture && !dragging && getScrollX() != targetScroll()) settle(selected);
            recycleVelocity();
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) begin(event);
        if (ignoreGesture) return true;
        if (velocity != null) velocity.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE) {
            detectDrag(event);
            if (dragging) followFinger(event);
        } else if (action == MotionEvent.ACTION_UP) {
            if (dragging) {
                velocity.computeCurrentVelocity(1000);
                float speed = velocity.getXVelocity();
                int target = Math.abs(speed) >= flingVelocity
                    ? speed < 0 ? 1 : 0 : getScrollX() > getWidth() / 2 ? 1 : 0;
                settle(target);
            } else {
                if (getScrollX() != targetScroll()) settle(selected);
                performClick();
            }
            recycleVelocity();
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
            settle(selected);
            ignoreGesture = true;
            recycleVelocity();
        }
        return true;
    }

    private void followFinger(MotionEvent event) {
        int x = Math.round(startScroll + startX - event.getX());
        scrollTo(Math.max(0, Math.min(getWidth(), x)), 0);
    }

    private void settle(int page) {
        selected = hasMusic ? page : 1;
        dragging = false;
        int target = targetScroll();
        settling = getScrollX() != target;
        updateVisibility();
        onSelection.accept(selected);
        if (settling) {
            scroller.startScroll(getScrollX(), 0, target - getScrollX(), 0, 240);
            postInvalidateOnAnimation();
        }
    }

    @Override public void computeScroll() {
        if (!settling) return;
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        } else {
            settling = false;
            scrollTo(targetScroll(), 0);
            updateVisibility();
        }
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.HorizontalScrollView");
        info.setScrollable(hasMusic);
        if (hasMusic) info.addAction(selected == 0
            ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
            : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (hasMusic && action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && selected == 0) { settle(1); return true; }
        if (hasMusic && action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && selected == 1) { settle(0); return true; }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (hasMusic && key == KeyEvent.KEYCODE_DPAD_RIGHT && selected == 0) { settle(1); return true; }
        if (hasMusic && key == KeyEvent.KEYCODE_DPAD_LEFT && selected == 1) { settle(0); return true; }
        return super.onKeyDown(key, event);
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void recycleVelocity() {
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }

    @Override protected void onDetachedFromWindow() {
        scroller.abortAnimation();
        recycleVelocity();
        super.onDetachedFromWindow();
    }
}
