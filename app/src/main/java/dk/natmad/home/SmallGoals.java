package dk.natmad.home;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local, date-scoped goals. Previous days are retained without carrying unfinished goals forward. */
final class SmallGoals {
    static final class Goal {
        final String id;
        String title;
        boolean done;

        Goal(String id, String title, boolean done) {
            this.id = id;
            this.title = title;
            this.done = done;
        }
    }

    private final SharedPreferences preferences;

    SmallGoals(Context context) {
        preferences = context.getSharedPreferences("small_goals", Context.MODE_PRIVATE);
    }

    List<Goal> read(LocalDate date) {
        List<Goal> goals = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(preferences.getString(date.toString(), "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.getJSONObject(i);
                goals.add(new Goal(item.getString("id"), item.getString("title"), item.getBoolean("done")));
            }
        } catch (JSONException exception) {
            android.util.Log.e("QuietHome", "Unable to read small goals", exception);
        }
        return goals;
    }

    void add(LocalDate date, String title) {
        if (title.trim().isEmpty()) return;
        List<Goal> goals = read(date);
        goals.add(new Goal(UUID.randomUUID().toString(), title.trim(), false));
        save(date, goals);
    }

    void update(LocalDate date, String id, String title, boolean done) {
        if (title.trim().isEmpty()) return;
        List<Goal> goals = read(date);
        for (Goal goal : goals) {
            if (goal.id.equals(id)) { goal.title = title.trim(); goal.done = done; }
        }
        save(date, goals);
    }

    void delete(LocalDate date, String id) {
        List<Goal> goals = read(date);
        goals.removeIf(goal -> goal.id.equals(id));
        save(date, goals);
    }

    private void save(LocalDate date, List<Goal> goals) {
        JSONArray saved = new JSONArray();
        try {
            for (Goal goal : goals) {
                saved.put(new JSONObject().put("id", goal.id).put("title", goal.title).put("done", goal.done));
            }
        } catch (JSONException exception) { throw new IllegalStateException(exception); }
        preferences.edit().putString(date.toString(), saved.toString()).apply();
    }
}
