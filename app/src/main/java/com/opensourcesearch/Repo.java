package com.opensourcesearch;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** GitHub 仓库数据模型。 */
public class Repo {

    public long id;
    public String name;
    public String fullName;
    public String description;
    public String htmlUrl;
    public int stars;
    public int forks;
    public int issues;
    public String language;
    public String updatedAt;
    public String ownerLogin;
    public String ownerAvatar;

    public static Repo fromJson(JSONObject o) {
        Repo r = new Repo();
        r.id = o.optLong("id");
        r.name = o.optString("name");
        r.fullName = o.optString("full_name");
        r.description = o.optString("description");
        r.htmlUrl = o.optString("html_url");
        r.stars = o.optInt("stargazers_count");
        r.forks = o.optInt("forks_count");
        r.issues = o.optInt("open_issues_count");
        r.language = o.optString("language");
        r.updatedAt = o.optString("updated_at");
        JSONObject owner = o.optJSONObject("owner");
        if (owner != null) {
            r.ownerLogin = owner.optString("login");
            r.ownerAvatar = owner.optString("avatar_url");
        }
        return r;
    }

    /** 星标数格式化：1234 -> 1.2k，1234567 -> 1.2M */
    public static String formatCount(long n) {
        if (n < 1000) {
            return String.valueOf(n);
        }
        if (n < 10000) {
            String s = String.format(Locale.US, "%.1fk", n / 1000.0);
            return s.replace(".0k", "k");
        }
        if (n < 1000000) {
            return (n / 1000) + "k";
        }
        String s = String.format(Locale.US, "%.1fM", n / 1000000.0);
        return s.replace(".0M", "M");
    }

    /** 相对时间：updated_at(ISO8601) -> "3 天前更新" */
    public static String relativeTime(String iso) {
        if (iso == null || iso.length() < 10) {
            return "";
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = sdf.parse(iso.replace("Z", "").replace("T", "T"));
            if (d == null) {
                return "";
            }
            long days = (System.currentTimeMillis() - d.getTime()) / 86400000L;
            if (days <= 0) {
                return "今天更新";
            }
            if (days == 1) {
                return "昨天更新";
            }
            if (days < 30) {
                return days + " 天前更新";
            }
            if (days < 365) {
                return (days / 30) + " 个月前更新";
            }
            return (days / 365) + " 年前更新";
        } catch (Exception e) {
            return "";
        }
    }

    private static final Map<String, Integer> LANG_COLORS = new HashMap<>();

    static {
        LANG_COLORS.put("Java", 0xFFB07219);
        LANG_COLORS.put("Kotlin", 0xFFA97BFF);
        LANG_COLORS.put("JavaScript", 0xFFF1E05A);
        LANG_COLORS.put("TypeScript", 0xFF3178C6);
        LANG_COLORS.put("Python", 0xFF3572A5);
        LANG_COLORS.put("C", 0xFF555555);
        LANG_COLORS.put("C++", 0xFFF34B7D);
        LANG_COLORS.put("C#", 0xFF178600);
        LANG_COLORS.put("Go", 0xFF00ADD8);
        LANG_COLORS.put("Rust", 0xFFDEA584);
        LANG_COLORS.put("Swift", 0xFFF05138);
        LANG_COLORS.put("Objective-C", 0xFF438EFF);
        LANG_COLORS.put("Dart", 0xFF00B4AB);
        LANG_COLORS.put("Ruby", 0xFF701516);
        LANG_COLORS.put("PHP", 0xFF4F5D95);
        LANG_COLORS.put("HTML", 0xFFE34C26);
        LANG_COLORS.put("CSS", 0xFF563D7C);
        LANG_COLORS.put("Vue", 0xFF41B883);
        LANG_COLORS.put("Shell", 0xFF89E051);
        LANG_COLORS.put("Lua", 0xFF000080);
        LANG_COLORS.put("Scala", 0xFFC22D40);
        LANG_COLORS.put("Perl", 0xFF0298C3);
        LANG_COLORS.put("Haskell", 0xFF5E5086);
        LANG_COLORS.put("Elixir", 0xFF6E4A7E);
        LANG_COLORS.put("Clojure", 0xFFDB5855);
        LANG_COLORS.put("Jupyter Notebook", 0xFFDA5B0B);
        LANG_COLORS.put("Assembly", 0xFF6E4C13);
        LANG_COLORS.put("Makefile", 0xFF427819);
        LANG_COLORS.put("Dockerfile", 0xFF384D54);
        LANG_COLORS.put("MATLAB", 0xFFE16737);
        LANG_COLORS.put("R", 0xFF198CE7);
    }

    /** 编程语言主题色（GitHub linguist），未知语言返回默认灰色。 */
    public static int languageColor(String language) {
        if (language == null) {
            return 0xFF8B949E;
        }
        Integer color = LANG_COLORS.get(language);
        return color == null ? 0xFF8B949E : color;
    }
}
