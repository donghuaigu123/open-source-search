package com.opensourcesearch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 最近一次搜索结果的磁盘缓存。
 *
 * <p>仅靠 {@code onSaveInstanceState} 的 Bundle 不足以覆盖所有场景：
 * 进入内置浏览器后 WebView 占用大量内存，系统很容易回收后台进程；
 * 部分定制系统在杀后台时甚至不保留 Activity 状态。将结果持久化到
 * SharedPreferences 后，无论进程是否被回收、从最近任务还是桌面图标
 * 返回，都能原样恢复搜索结果，无需重新搜索。
 */
public final class ResultCache {

    private static final String PREF = "result_cache";
    private static final String KEY_PAYLOAD = "payload";

    private ResultCache() {
    }

    /** 保存最近一次成功的搜索结果（异步写入，调用方无需关心 IO）。 */
    public static void save(Context ctx, String query, int totalCount, List<Repo> items) {
        if (ctx == null || items == null || items.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("query", query == null ? "" : query);
            root.put("total", totalCount);
            root.put("savedAt", System.currentTimeMillis());
            JSONArray arr = new JSONArray();
            for (Repo r : items) {
                arr.put(r.toJson());
            }
            root.put("items", arr);
            prefs(ctx).edit().putString(KEY_PAYLOAD, root.toString()).apply();
        } catch (Exception ignore) {
            // 缓存失败不影响主流程
        }
    }

    /** 载入缓存；无缓存或解析失败返回 {@code null}。 */
    public static Loaded load(Context ctx) {
        if (ctx == null) {
            return null;
        }
        try {
            String raw = prefs(ctx).getString(KEY_PAYLOAD, null);
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            JSONObject root = new JSONObject(raw);
            JSONArray arr = root.optJSONArray("items");
            if (arr == null || arr.length() == 0) {
                return null;
            }
            List<Repo> items = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                Repo r = Repo.fromJson(arr.optJSONObject(i));
                if (r != null) {
                    items.add(r);
                }
            }
            if (items.isEmpty()) {
                return null;
            }
            Loaded out = new Loaded();
            out.query = root.optString("query");
            out.totalCount = root.optInt("total");
            out.items = items;
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** 清除缓存。 */
    public static void clear(Context ctx) {
        if (ctx != null) {
            prefs(ctx).edit().remove(KEY_PAYLOAD).apply();
        }
    }

    /** 缓存内容。 */
    public static final class Loaded {
        public String query;
        public int totalCount;
        public List<Repo> items;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
