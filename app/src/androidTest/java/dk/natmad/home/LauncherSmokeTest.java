package dk.natmad.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Runs against a real Android system without a third-party test runtime. */
public class LauncherSmokeTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity = startActivitySync(intent);
            waitForIdleSync();
            runOnMainSync(() -> {
                ViewGroup root = activity.findViewById(android.R.id.content);
                require(find(root, "Everything else") != null, "Home navigation is missing");
                require(find(root, "Distractions") == null, "Distractions must not appear on home");
                require(find(root, "Organize apps") == null, "Home must not be editable in the UI");
                List<String> expectedHome = new ArrayList<>();
                for (String[] alternatives : HomeApps.PACKAGES) {
                    for (String packageId : alternatives) {
                        if (getTargetContext().getPackageManager().getLaunchIntentForPackage(packageId) != null) {
                            try {
                                expectedHome.add(getTargetContext().getPackageManager()
                                    .getApplicationInfo(packageId, 0)
                                    .loadLabel(getTargetContext().getPackageManager()).toString());
                            } catch (android.content.pm.PackageManager.NameNotFoundException exception) {
                                throw new AssertionError("Installed package disappeared during test", exception);
                            }
                            break;
                        }
                    }
                }
                require(appLabels(root).equals(expectedHome), "Home must use Android labels in configured package order");
                find(root, "Everything else").performClick();
                require(find(root, "Back") != null, "App drawer did not open");
                EditText search = findSearch(root);
                require(search != null, "Search field is missing");
                List<String> before = appLabels(root);
                require(!expectedHome.isEmpty(), "Test device needs at least one configured home app");
                Intent allApps = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (android.content.pm.ResolveInfo info : getTargetContext().getPackageManager().queryIntentActivities(allApps, 0)) {
                    if (info.activityInfo.packageName.equals(getTargetContext().getPackageName())) continue;
                    String appLabel = info.loadLabel(getTargetContext().getPackageManager()).toString();
                    require(before.contains(appLabel), "Drawer must include every app, including home apps: " + appLabel);
                }
                List<String> alphabetical = new ArrayList<>(before);
                alphabetical.sort(Collator.getInstance());
                require(before.equals(alphabetical), "Drawer must be alphabetical");
                require(!before.isEmpty(), "Test device needs at least one launchable app");
                String label = before.get(0);
                String query = label.substring(0, Math.min(3, label.length())).toUpperCase(Locale.ROOT);
                search.setText("  " + query + "  ");
                List<String> filtered = appLabels(root);
                require(filtered.contains(label), "Case-insensitive partial search lost the matching app");
                for (String match : filtered) require(AppRules.matchesSearch(query, match), "Search retained a nonmatching app");
                search.setText("no-such-app-9f2b7a");
                require(appLabels(root).isEmpty(), "No-match query retained apps");
                require(find(root, "No apps found.") != null, "No-match message missing");
                find(root, "Clear").performClick();
                require(appLabels(root).equals(before), "Clearing search must restore the full alphabetical list");
                search.setText(query);
                find(root, "Back").performClick();
                require(findSearch(root) == null, "Search should only be in the drawer");
                find(root, "Everything else").performClick();
                require(findSearch(root).getText().length() == 0, "Reopening the drawer must reset search");
                require(appLabels(root).equals(before), "Drawer should reopen with all apps");
                // Exercise the real search-to-launch path when YouTube is installed.
                android.content.pm.PackageManager pm = getTargetContext().getPackageManager();
                Intent youtube = pm.getLaunchIntentForPackage("com.google.android.youtube");
                if (youtube != null) {
                    String youtubeLabel = pm.resolveActivity(youtube, 0).loadLabel(pm).toString();
                    findSearch(root).setText(youtubeLabel);
                    find(root, youtubeLabel).performClick();
                    require(find(root, "Open") != null && !find(root, "Open").isEnabled(),
                        "A hard app opened from search must wait before opening");
                    find(root, "Go back").performClick();
                    require(findSearch(root).getText().toString().equals(youtubeLabel),
                        "Cancelling must return to the search results");
                }
                find(root, "Back").performClick();
            });
            FrictionSmokeChecks.run(this, activity);
            MediaSmokeChecks.run(this, activity);
            DisplayModeSmokeChecks.run(this, activity);
            result.putString("stream", "Launcher home, search, friction, media, and display mode smoke tests passed on Android " + android.os.Build.VERSION.RELEASE + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAILED: " + error + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static List<String> appLabels(View root) {
        List<String> labels = new ArrayList<>();
        visit(root, view -> {
            if (view instanceof TextView && ((TextView) view).getCompoundDrawablesRelative()[0] != null)
                labels.add(((TextView) view).getText().toString());
        });
        return labels;
    }

    private static EditText findSearch(View root) {
        EditText[] result = {null};
        visit(root, view -> { if (view instanceof EditText) result[0] = (EditText) view; });
        return result[0];
    }

    private static TextView find(View root, String text) {
        TextView[] result = {null};
        visit(root, view -> {
            if (view instanceof TextView && ((TextView) view).getText().toString().equals(text)) result[0] = (TextView) view;
        });
        return result[0];
    }

    private static void visit(View view, java.util.function.Consumer<View> visitor) {
        visitor.accept(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) visit(group.getChildAt(i), visitor);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
