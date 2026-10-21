package com.opensourcesearch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** 搜索历史记录存储（最多 15 条，最新在前，自动去重）。 */
public final class HistoryStore {

    public static final int MAX = 15;

    private static final String NAME = "oss_history";
    private static final String KEY = "history_json";

    private HistoryStore() {
    }

    public static List<String> all(Context c) {
        List<String> list = new ArrayList<>();
        String raw = sp(c).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String q = arr.optString(i);
                if (q != null && !q.trim().isEmpty()) {
                    list.add(q);
                }
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static void add(Context c, String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        final String q = query.trim();
        List<String> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(q)) {
                list.remove(i);
                break;
            }
        }
        list.add(0, q);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        save(c, list);
    }

    public static void remove(Context c, String query) {
        List<String> list = all(c);
        if (list.remove(query)) {
            save(c, list);
        }
    }

    public static void clear(Context c) {
        save(c, new ArrayList<>());
    }

    private static void save(Context c, List<String> list) {
        JSONArray arr = new JSONArray();
        for (String q : list) {
            arr.put(q);
        }
        sp(c).edit().putString(KEY, arr.toString()).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
