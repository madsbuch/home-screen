package dk.natmad.home;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int WHITE = Color.rgb(225, 225, 225);
    private static final int MUTED = Color.rgb(153, 153, 153);
    private final Map<String, App> apps = new LinkedHashMap<>();
    private final Map<String, String> home = new LinkedHashMap<>();
    private final Set<String> homePackages = new HashSet<>();
    private LinearLayout root;
    private String screen = "home";
    private String searchQuery = "";
    private LinearLayout searchResults;
    private MediaControls mediaControls;
    private boolean resumed;

    private static class App {
        final String id;
        final String label;
        final String packageName;
        App(String id, String label, String packageName) {
            this.id = id; this.label = label; this.packageName = packageName;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::showHome);
        }
        refreshApps();
        selectHomeApps();
        if (state != null && state.getBoolean("drawer", false)) {
            searchQuery = state.getString("query", "");
            showList(false);
        } else showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        refreshApps();
        selectHomeApps();
        if (screen.equals("home")) showHome();
        else updateSearchResults();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("drawer", screen.equals("everyday"));
        state.putString("query", searchQuery);
        super.onSaveInstanceState(state);
    }

    @Override protected void onPause() {
        resumed = false;
        if (mediaControls != null) mediaControls.stop();
        // A backgrounded attempt must never finish or retain credit toward opening an app.
        if (screen.equals("gate")) showHome();
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showHome();
    }

    @Override public void onBackPressed() { showHome(); }

    private void refreshApps() {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> found = new ArrayList<>();
        for (ResolveInfo info : getPackageManager().queryIntentActivities(query, 0)) {
            if (info.activityInfo.packageName.equals(getPackageName())) continue;
            ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            found.add(new App(component.flattenToString(), info.loadLabel(getPackageManager()).toString(),
                info.activityInfo.packageName));
        }
        apps.clear();
        for (App app : found) apps.put(app.id, app);
    }

    private void selectHomeApps() {
        home.clear();
        homePackages.clear();
        for (String[] alternatives : HomeApps.PACKAGES) {
            for (String packageId : alternatives) {
                // Resolve the app's actual entry point; labels and activity class names may change.
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageId);
                if (launchIntent == null) continue;
                ComponentName component = launchIntent.getComponent();
                if (component == null) component = launchIntent.resolveActivity(getPackageManager());
                if (component == null || !component.getPackageName().equals(packageId)) continue;
                String label;
                try {
                    label = getPackageManager().getApplicationInfo(packageId, 0)
                        .loadLabel(getPackageManager()).toString();
                } catch (android.content.pm.PackageManager.NameNotFoundException exception) {
                    // The app may have been uninstalled since resolving its entry point.
                    continue;
                }
                String id = component.flattenToString();
                if (homePackages.add(packageId)) {
                    // A package's entry point can be CATEGORY_INFO instead of CATEGORY_LAUNCHER.
                    apps.putIfAbsent(id, new App(id, label, packageId));
                    home.put(id, label);
                }
                break;
            }
        }
    }

    private LinearLayout page(String name) {
        if (mediaControls != null) mediaControls.stop();
        mediaControls = null;
        screen = name;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(30), dp(12), dp(30), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(dp(30) + bars.left, dp(12) + bars.top, dp(30) + bars.right, dp(8) + bars.bottom);
            } else {
                view.setPadding(dp(30) + insets.getSystemWindowInsetLeft(), dp(12) + insets.getSystemWindowInsetTop(),
                    dp(30) + insets.getSystemWindowInsetRight(), dp(8) + insets.getSystemWindowInsetBottom());
            }
            return insets.consumeSystemWindowInsets();
        });
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(root);
        root.requestApplyInsets();
        return root;
    }

    private void showHome() {
        hideKeyboard();
        searchQuery = "";
        searchResults = null;
        page("home");
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(WHITE);
        clock.setTextSize(64);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setPadding(0, dp(14), 0, 0);
        root.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(MUTED);
        date.setTextSize(15);
        date.setPadding(0, dp(6), 0, dp(22));
        root.addView(date);

        LinearLayout favorites = scrollArea();
        for (String id : home.keySet()) {
            App app = apps.get(id);
            if (app != null) favorites.addView(appAction(app, home.get(id), 27, () -> requestLaunch(app)));
        }
        if (home.isEmpty()) {
            favorites.addView(copy("Your home, with a little less.", 23, WHITE));
            favorites.addView(copy("Your other apps are in Everything else.", 16, MUTED));
        }
        mediaControls = new MediaControls(this);
        root.addView(mediaControls, new LinearLayout.LayoutParams(-1, -2));
        if (resumed) mediaControls.start();
        root.addView(action("Everything else", 17, WHITE, () -> showList(true)));
    }

    private void showList(boolean focusSearch) {
        page("everyday");
        root.addView(action("Back", 16, MUTED, this::showHome));
        root.addView(copy("Everything else", 30, WHITE));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(20);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("Search apps");
        input.setContentDescription("Search apps");
        input.setMinHeight(dp(48));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        input.setText(searchQuery);
        searchRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = action("Clear", 15, MUTED, () -> input.setText(""));
        clear.setMinWidth(dp(56));
        clear.setGravity(Gravity.CENTER);
        clear.setContentDescription("Clear search");
        clear.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
        searchRow.addView(clear, new LinearLayout.LayoutParams(-2, -2));
        root.addView(searchRow);
        searchResults = scrollArea();
        searchResults.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                clear.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                updateSearchResults();
            }
            public void afterTextChanged(Editable s) {}
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            hideKeyboard();
            return true;
        });
        updateSearchResults();
        if (focusSearch) {
            input.requestFocus();
            input.post(() -> {
                if (input.isAttachedToWindow() && screen.equals("everyday")) {
                    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private void updateSearchResults() {
        if (searchResults == null) return;
        searchResults.removeAllViews();
        List<App> sortedApps = new ArrayList<>(apps.values());
        Collator collator = Collator.getInstance();
        sortedApps.sort((a, b) -> collator.compare(a.label, b.label));
        int count = 0;
        for (App app : sortedApps) {
            if (AppRules.matchesSearch(searchQuery, app.label)) {
                searchResults.addView(appAction(app, app.label, 23, () -> requestLaunch(app)));
                count++;
            }
        }
        if (count == 0) searchResults.addView(copy(searchQuery.trim().isEmpty()
            ? "No apps available." : "No apps found.", 16, MUTED));
        ((ScrollView) searchResults.getParent()).scrollTo(0, 0);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    private void requestLaunch(App app) {
        AppFriction.Level level = AppFriction.forPackage(app.packageName);
        if (level == AppFriction.Level.NONE) {
            launch(app);
            return;
        }
        boolean fromDrawer = screen.equals("everyday");
        hideKeyboard();
        searchResults = null;
        page("gate");
        scrollArea().addView(new LaunchGate(this, app.label, level, () -> launch(app), () -> {
            if (fromDrawer) showList(false);
            else showHome();
        }));
    }

    private void launch(App app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName.unflattenFromString(app.id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            showHome();
        } catch (android.content.ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "This app is unavailable.", Toast.LENGTH_LONG).show();
            refreshApps();
            selectHomeApps();
            showHome();
        }
    }

    private LinearLayout scrollArea() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return content;
    }

    private TextView copy(String label, int size, int color) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(8));
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private TextView appAction(App app, String label, int size, Runnable callback) {
        TextView view = action(label, size, WHITE, callback);
        // A compound drawable keeps the name and mark in one accessible tap target.
        view.setCompoundDrawablesRelative(new AppIdenticon(app.packageName, MUTED, dp(24)), null, null, null);
        view.setCompoundDrawablePadding(dp(16));
        return view;
    }

    private TextView action(String label, int size, int color, Runnable callback) {
        TextView view = copy(label, size, color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(48));
        view.setFocusable(true);
        view.setClickable(true);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        android.util.TypedValue background = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
        view.setBackgroundResource(background.resourceId);
        view.setOnClickListener(ignored -> callback.run());
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
