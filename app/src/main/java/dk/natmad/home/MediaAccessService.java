package dk.natmad.home;

import android.service.notification.NotificationListenerService;
import java.util.HashSet;
import java.util.Set;

/** Grants access to media sessions. Notification contents are never read or stored. */
public final class MediaAccessService extends NotificationListenerService {
    // Service lifecycle and view callbacks run on the main thread.
    static final Set<Runnable> accessListeners = new HashSet<>();

    @Override public void onListenerConnected() { notifyAccessChanged(); }
    @Override public void onListenerDisconnected() { notifyAccessChanged(); }

    private void notifyAccessChanged() {
        for (Runnable listener : new HashSet<>(accessListeners)) listener.run();
    }
}
