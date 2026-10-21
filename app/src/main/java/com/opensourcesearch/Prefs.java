package com.opensourcesearch;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** 应用偏好存储。 */
public final class Prefs {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_DAY = 1;
    public static final int THEME_NIGHT = 2;

    private static final String NAME = "oss_prefs";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_DESKTOP = "desktop_mode";

    private Prefs() {
    }

    public static int getThemeMode(Context c) {
        return sp(c).getInt(KEY_THEME, THEME_SYSTEM);
    }

    public static void setThemeMode(Context c, int mode) {
        sp(c).edit().putInt(KEY_THEME, mode).apply();
    }

    public static int toNightMode(int themeMode) {
        switch (themeMode) {
            case THEME_DAY:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case THEME_NIGHT:
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    public static boolean isDesktopMode(Context c) {
        return sp(c).getBoolean(KEY_DESKTOP, false);
    }

    public static void setDesktopMode(Context c, boolean desktop) {
        sp(c).edit().putBoolean(KEY_DESKTOP, desktop).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
