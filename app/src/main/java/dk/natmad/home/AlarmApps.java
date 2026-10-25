package dk.natmad.home;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.AlarmClock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Finds alarm apps by supported actions, with a OnePlus Clock compatibility fallback. */
final class AlarmApps {
    private AlarmApps() {}

    static List<Intent> launchIntents(PackageManager manager) {
        Set<String> packages = new LinkedHashSet<>();
        for (String action : new String[] {AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM}) {
            Intent query = new Intent(action);
            ResolveInfo preferred = manager.resolveActivity(query, PackageManager.MATCH_DEFAULT_ONLY);
            if (preferred != null && preferred.activityInfo != null) {
                packages.add(preferred.activityInfo.packageName);
            }
            for (ResolveInfo info : manager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)) {
                if (info.activityInfo != null) packages.add(info.activityInfo.packageName);
            }
        }
        // OnePlus fallback also works when its clock advertises neither standard action.
        // Package ID: https://play.google.com/store/apps/details?id=com.oneplus.deskclock
        packages.add("com.oneplus.deskclock");
        List<Intent> launches = new ArrayList<>();
        for (String packageName : packages) {
            Intent launch = manager.getLaunchIntentForPackage(packageName);
            if (launch != null) launches.add(launch);
        }
        return launches;
    }
}
