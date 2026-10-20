package dk.natmad.home;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;

/** Shared monochrome, rounded controls with visible touch and keyboard feedback. */
final class ControlStyle {
    static final int WHITE = Color.rgb(225, 225, 225);

    private ControlStyle() {}

    static void rounded(View view, boolean primary) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {-android.R.attr.state_enabled}, shape(view, primary, false, false));
        states.addState(new int[] {android.R.attr.state_focused}, shape(view, primary, true, true));
        states.addState(new int[] {}, shape(view, primary, true, false));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(view, 1000));
        view.setBackgroundTintList(null);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33000000 : 0x44ffffff), states, mask));
        view.setElevation(0);
        view.setStateListAnimator(null);
    }

    static void pill(Button button, boolean primary) {
        rounded(button, primary);
        button.setAllCaps(false);
        button.setMinHeight(dp(button, 56));
        button.setPadding(dp(button, 24), dp(button, 12), dp(button, 24), dp(button, 12));
        button.setTextColor(new ColorStateList(new int[][] {
            new int[] {-android.R.attr.state_enabled}, new int[] {}
        }, new int[] {0xff888888, primary ? Color.BLACK : WHITE}));
    }

    private static GradientDrawable shape(View view, boolean primary, boolean enabled, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(primary ? (enabled ? WHITE : 0xff222222) : Color.BLACK);
        shape.setCornerRadius(dp(view, 1000));
        if (!primary) shape.setStroke(dp(view, focused ? 2 : 1), focused ? WHITE : 0xff555555);
        else if (focused) shape.setStroke(dp(view, 3), 0xff888888);
        return shape;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
