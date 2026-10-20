package dk.natmad.home;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** A foreground-only view of the system's current media sessions. */
final class MediaControls extends LinearLayout {
    private final MediaSessionManager manager;
    private final ComponentName accessService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers = new ArrayList<>();
    private final TextView title;
    private final TextView artist;
    private final LinearLayout player;
    private final Button setup;
    private final ImageButton previous;
    private final ImageButton toggle;
    private final ImageButton next;
    private final ImageButton stopPlayback;
    private MediaController selected;
    private boolean started;
    private boolean listening;
    private final Runnable accessChanged = this::refreshAccess;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::replaceSessions;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { render(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { render(); }
        @Override public void onSessionDestroyed() { refreshAccess(); }
    };

    MediaControls(Context context) {
        super(context);
        manager = context.getSystemService(MediaSessionManager.class);
        accessService = new ComponentName(context, MediaAccessService.class);
        setOrientation(VERTICAL);
        setVisibility(GONE);

        setup = control("Enable music controls", this::requestAccess);
        setup.setTextSize(14);
        setup.setTextColor(Color.rgb(153, 153, 153));
        setup.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        android.util.TypedValue background = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
        setup.setBackgroundResource(background.resourceId);
        setup.setPadding(0, dp(8), 0, dp(8));
        addView(setup, new LayoutParams(-1, -2));

        player = new LinearLayout(context);
        player.setOrientation(VERTICAL);
        player.setPadding(0, dp(8), 0, dp(8));
        View divider = new View(context);
        divider.setBackgroundColor(0xff333333);
        LayoutParams dividerLayout = new LayoutParams(-1, dp(1));
        dividerLayout.bottomMargin = dp(16);
        player.addView(divider, dividerLayout);
        title = text(18);
        artist = text(14);
        artist.setPadding(0, dp(4), 0, 0);
        artist.setTextColor(Color.rgb(153, 153, 153));
        player.addView(title, new LayoutParams(-1, -2));
        player.addView(artist, new LayoutParams(-1, -2));
        LinearLayout buttons = new LinearLayout(context);
        buttons.setPadding(0, dp(16), 0, dp(4));
        previous = mediaControl("Previous track", R.drawable.ic_media_previous, false,
            () -> transport(PlaybackState.ACTION_SKIP_TO_PREVIOUS));
        toggle = mediaControl("Pause", R.drawable.ic_media_pause, true, () -> transport(isPlaying(selected)
            ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY));
        next = mediaControl("Next track", R.drawable.ic_media_next, false,
            () -> transport(PlaybackState.ACTION_SKIP_TO_NEXT));
        stopPlayback = mediaControl("Stop playback", R.drawable.ic_media_stop, false,
            () -> transport(PlaybackState.ACTION_STOP));
        for (ImageButton control : new ImageButton[] {previous, toggle, next, stopPlayback}) {
            FrameLayout slot = new FrameLayout(context);
            slot.addView(control, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
            buttons.addView(slot, new LayoutParams(0, dp(56), 1));
        }
        player.addView(buttons);
        addView(player, new LayoutParams(-1, -2));
    }

    void start() {
        if (started) return;
        started = true;
        MediaAccessService.accessListeners.add(accessChanged);
        refreshAccess();
    }

    void stop() {
        started = false;
        MediaAccessService.accessListeners.remove(accessChanged);
        if (listening) manager.removeOnActiveSessionsChangedListener(sessionsChanged);
        listening = false;
        clearSessions();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void refreshAccess() {
        if (!started) return;
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(sessionsChanged, accessService, handler);
                listening = true;
            }
            replaceSessions(manager.getActiveSessions(accessService));
        } catch (SecurityException exception) {
            if (listening) manager.removeOnActiveSessionsChangedListener(sessionsChanged);
            listening = false;
            clearSessions();
            player.setVisibility(GONE);
            setup.setVisibility(VISIBLE);
            setVisibility(VISIBLE);
        }
    }

    private void clearSessions() {
        for (MediaController controller : controllers) controller.unregisterCallback(callback);
        controllers.clear();
        selected = null;
    }

    private void replaceSessions(List<MediaController> sessions) {
        if (!started) return;
        clearSessions();
        if (sessions != null) {
            controllers.addAll(sessions);
            for (MediaController controller : controllers) controller.registerCallback(callback, handler);
        }
        render();
    }

    private void render() {
        if (!started) return;
        // Prefer a playing session over a paused player, preserving Android's priority order.
        selected = null;
        int best = 0;
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            int priority = isPlaying(controller) ? 2
                : state != null && state.getState() == PlaybackState.STATE_PAUSED ? 1 : 0;
            if (priority > best) {
                selected = controller;
                best = priority;
            }
        }
        setup.setVisibility(GONE);
        player.setVisibility(selected == null ? GONE : VISIBLE);
        setVisibility(selected == null ? GONE : VISIBLE);
        if (selected == null) return;
        MediaMetadata metadata = selected.getMetadata();
        CharSequence track = metadata == null ? null : metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (TextUtils.isEmpty(track) && metadata != null) track = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence performer = metadata == null ? null : metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(performer) && metadata != null)
            performer = metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        title.setText(TextUtils.isEmpty(track) ? appName(selected.getPackageName()) : track);
        artist.setText(performer);
        artist.setVisibility(TextUtils.isEmpty(performer) ? GONE : VISIBLE);
        boolean playing = isPlaying(selected);
        toggle.setImageResource(playing ? R.drawable.ic_media_pause : R.drawable.ic_media_play);
        toggle.setContentDescription(playing ? "Pause" : "Play");
        toggle.setTooltipText(toggle.getContentDescription());
        enable(previous, supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS));
        enable(toggle, supports(isPlaying(selected) ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY));
        enable(next, supports(PlaybackState.ACTION_SKIP_TO_NEXT));
        enable(stopPlayback, supports(PlaybackState.ACTION_STOP));
        stopPlayback.setTooltipText(supports(PlaybackState.ACTION_STOP)
            ? "Stop playback" : "This player does not support Stop");
    }

    private static boolean isPlaying(MediaController controller) {
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        if (state == null) return false;
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default: return false;
        }
    }

    private boolean supports(long action) {
        PlaybackState state = selected == null ? null : selected.getPlaybackState();
        if (state == null) return false;
        long allowed = action;
        if (action == PlaybackState.ACTION_PLAY || action == PlaybackState.ACTION_PAUSE)
            allowed |= PlaybackState.ACTION_PLAY_PAUSE;
        return (state.getActions() & allowed) != 0;
    }

    private void transport(long action) {
        if (!started || !supports(action)) return;
        try {
            MediaController.TransportControls transport = selected.getTransportControls();
            if (action == PlaybackState.ACTION_PLAY) transport.play();
            else if (action == PlaybackState.ACTION_PAUSE) transport.pause();
            else if (action == PlaybackState.ACTION_SKIP_TO_PREVIOUS) transport.skipToPrevious();
            else if (action == PlaybackState.ACTION_SKIP_TO_NEXT) transport.skipToNext();
            else if (action == PlaybackState.ACTION_STOP) transport.stop();
        } catch (SecurityException exception) {
            refreshAccess();
        }
    }

    private void requestAccess() {
        new AlertDialog.Builder(getContext())
            .setTitle("Music on your home screen")
            .setMessage("Allow notification access for Quiet Home in Android Settings to show and control "
                + "playing media. Android grants access to notifications; Quiet Home only uses media "
                + "sessions and does not read or save notification contents.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open settings", (dialog, which) -> {
                try {
                    getContext().startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (android.content.ActivityNotFoundException exception) {
                    Toast.makeText(getContext(), "Notification access settings are unavailable.", Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private CharSequence appName(String packageName) {
        try {
            PackageManager packages = getContext().getPackageManager();
            return packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0));
        } catch (PackageManager.NameNotFoundException exception) {
            return "Now playing";
        }
    }

    private TextView text(int size) {
        TextView view = new TextView(getContext());
        view.setTextSize(size);
        view.setTextColor(Color.rgb(225, 225, 225));
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private Button control(String label, Runnable action) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.rgb(225, 225, 225));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private ImageButton mediaControl(String label, int icon, boolean primary, Runnable action) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(primary ? Color.BLACK : ControlStyle.WHITE));
        button.setContentDescription(label);
        button.setTooltipText(label);
        button.setPadding(dp(16), dp(16), dp(16), dp(16));
        ControlStyle.rounded(button, primary);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void enable(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.35f);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
