package com.opensourcesearch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 浏览器书签存储。 */
public final class BookmarkStore {

    /** 书签条目。 */
    public static final class Bookmark {
        public final String title;
        public final String url;

        public Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private static final String NAME = "oss_bookmarks";
    private static final String KEY = "items";

    private BookmarkStore() {
    }

    public static synchronized List<Bookmark> all(Context c) {
        List<Bookmark> list = new ArrayList<>();
        String raw = sp(c).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    list.add(new Bookmark(o.optString("title"), o.optString("url")));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static synchronized boolean contains(Context c, String url) {
        if (url == null) {
            return false;
        }
        for (Bookmark b : all(c)) {
            if (url.equals(b.url)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void add(Context c, String title, String url) {
        List<Bookmark> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            if (url.equals(list.get(i).url)) {
                list.remove(i);
                break;
            }
        }
        list.add(0, new Bookmark(
                title == null || title.isEmpty() ? url : title, url));
        save(c, list);
    }

    public static synchronized void remove(Context c, String url) {
        List<Bookmark> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            if (url.equals(list.get(i).url)) {
                list.remove(i);
                break;
            }
        }
        save(c, list);
    }

    public static synchronized void clear(Context c) {
        save(c, new ArrayList<>());
    }

    private static void save(Context c, List<Bookmark> list) {
        JSONArray arr = new JSONArray();
        for (Bookmark b : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("title", b.title);
                o.put("url", b.url);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        sp(c).edit().putString(KEY, arr.toString()).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
