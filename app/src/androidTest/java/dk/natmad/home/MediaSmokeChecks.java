package dk.natmad.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.function.BooleanSupplier;

/** Uses real system media sessions and transport callbacks, without playing audio. */
final class MediaSmokeChecks {
    private static final long ACTIONS = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    static void run(Instrumentation test, Activity activity) {
        MediaSession[] session = {null};
        MediaSession[] other = {null};
        int[] previous = {0};
        ViewGroup root = activity.findViewById(android.R.id.content);
        try {
            test.runOnMainSync(() -> {
                activity.onBackPressed();
                session[0] = new MediaSession(activity, "Quiet Home playback test");
                session[0].setCallback(new MediaSession.Callback() {
                    @Override public void onPlay() { state(session[0], PlaybackState.STATE_PLAYING, ACTIONS); }
                    @Override public void onPause() { state(session[0], PlaybackState.STATE_PAUSED, ACTIONS); }
                    @Override public void onSkipToNext() { metadata(session[0], "Second track"); }
                    @Override public void onSkipToPrevious() { previous[0]++; }
                }, new Handler(Looper.getMainLooper()));
                metadata(session[0], "First track");
                state(session[0], PlaybackState.STATE_PLAYING, ACTIONS);
                session[0].setActive(true);
            });
            await(test, () -> visible(root, "First track") != null,
                "Playing session missing; enable notification access for Quiet Home on the test emulator");
            await(test, () -> visible(root, "Test artist") != null, "Artist missing");
            test.runOnMainSync(() -> visible(root, "Pause").performClick());
            await(test, () -> visible(root, "Play") != null, "Pause must leave a resume control");
            test.runOnMainSync(() -> visible(root, "Play").performClick());
            await(test, () -> visible(root, "Pause") != null, "Play did not resume the session");
            test.runOnMainSync(() -> visible(root, "Next").performClick());
            await(test, () -> visible(root, "Second track") != null, "Track metadata did not update");
            test.runOnMainSync(() -> visible(root, "Previous").performClick());
            await(test, () -> previous[0] == 1, "Previous did not reach the player");
            test.runOnMainSync(() -> {
                state(session[0], PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE);
                other[0] = new MediaSession(activity, "Paused competing player");
                metadata(other[0], "Paused other track");
                state(other[0], PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY);
                other[0].setActive(true);
            });
            await(test, () -> visible(root, "Next") != null && !visible(root, "Next").isEnabled(),
                "Unsupported skip action must be disabled");
            test.runOnMainSync(() -> require(visible(root, "Second track") != null,
                "Paused session displaced playing session"));
            test.runOnMainSync(() -> state(session[0], PlaybackState.STATE_STOPPED, ACTIONS));
            await(test, () -> visible(root, "Paused other track") != null,
                "Stopped session should yield to resumable session");
            test.runOnMainSync(() -> {
                other[0].release();
                session[0].release();
            });
            await(test, () -> visible(root, "Play") == null && visible(root, "Pause") == null,
                "Ended sessions left stale controls");
            test.runOnMainSync(() -> {
                // A session starting while the drawer is open must appear on returning home.
                visible(root, "Everything else").performClick();
                session[0] = new MediaSession(activity, "New player");
                state(session[0], PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PLAY_PAUSE);
                session[0].setActive(true);
                activity.onBackPressed();
            });
            await(test, () -> visible(root, "Pause") != null && visible(root, "Pause").isEnabled(),
                "Returning home did not discover a new session with toggle-only actions");
            test.runOnMainSync(() -> {
                require(visible(root, "Quiet Home") != null || visible(root, "Now playing") != null,
                    "Missing metadata needs a readable fallback");
                state(session[0], PlaybackState.STATE_NONE, 0);
            });
            await(test, () -> visible(root, "Pause") == null, "Inactive session should hide controls");
        } finally {
            test.runOnMainSync(() -> {
                if (session[0] != null) session[0].release();
                if (other[0] != null) other[0].release();
            });
        }
    }

    private static void state(MediaSession session, int state, long actions) {
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(state, 0, 1f).build());
    }

    private static void metadata(MediaSession session, String title) {
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Test artist").build());
    }

    private static void await(Instrumentation test, BooleanSupplier condition, String failure) {
        long deadline = SystemClock.uptimeMillis() + 5_000;
        boolean[] passed = {false};
        do {
            test.runOnMainSync(() -> passed[0] = condition.getAsBoolean());
            if (passed[0]) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError(failure);
    }

    private static TextView visible(View view, String label) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(label))
            return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = visible(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
