package com.opensourcesearch;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

/**
 * 应用入口：开启动态取色（Material You）并恢复用户主题模式。
 */
public class SearchApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Android 12+ 动态取色（Material You）
        DynamicColors.applyToActivitiesIfAvailable(this);
        // 恢复日夜主题模式
        AppCompatDelegate.setDefaultNightMode(Prefs.toNightMode(Prefs.getThemeMode(this)));
    }
}
