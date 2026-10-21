package com.opensourcesearch;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.opensourcesearch.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 首页：打开即见搜索框，搜索 GitHub 开源仓库（按星标从高到低，最多 30 条），
 * 点击结果跳转 GitHub 客户端（未安装则使用内置浏览器）。
 */
public class MainActivity extends AppCompatActivity implements RepoAdapter.Listener {

    /** 每页展示数量（需求：不超过 30 个） */
    private static final int PER_PAGE = 30;

    private static final List<String> HOT_KEYWORDS = Arrays.asList(
            "Android", "Jetpack Compose", "Kotlin", "Flutter", "React", "Vue",
            "LLM", "AI", "Rust", "Go", "Python", "TypeScript", "OpenAI", "Machine Learning");

    private ActivityMainBinding binding;
    private RepoAdapter adapter;
    private ExecutorService executor;
    private Handler mainHandler;
    private String currentQuery = "";
    private ActivityResultLauncher<String> notificationLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RepoAdapter(this);
        binding.recyclerView.setAdapter(adapter);

        binding.searchButton.setOnClickListener(v ->
                doSearch(binding.searchEditText.getText().toString()));
        binding.searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSearch = actionId == EditorInfo.IME_ACTION_SEARCH;
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isSearch || isEnter) {
                doSearch(binding.searchEditText.getText().toString());
                return true;
            }
            return false;
        });

        binding.btnClearHistory.setOnClickListener(v -> {
            HistoryStore.clear(this);
            renderHistory();
            Toast.makeText(this, "已清空搜索历史", Toast.LENGTH_SHORT).show();
        });

        binding.btnRetry.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(currentQuery)) {
                doSearch(currentQuery);
            }
        });

        setupHotChips();
        renderHistory();
        requestNotificationPermissionIfNeeded();
    }

    // ---------- 菜单 / 主题 ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        updateThemeMenuIcon(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_theme_toggle) {
            toggleTheme();
            return true;
        } else if (id == R.id.action_theme_settings) {
            showThemeDialog();
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void updateThemeMenuIcon(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_theme_toggle);
        if (item != null) {
            boolean night = resolveNight();
            item.setIcon(night ? R.drawable.ic_sun : R.drawable.ic_moon);
            item.setTitle(night ? R.string.theme_day : R.string.theme_night);
        }
    }

    private boolean resolveNight() {
        int mode = Prefs.getThemeMode(this);
        if (mode == Prefs.THEME_NIGHT) {
            return true;
        }
        if (mode == Prefs.THEME_DAY) {
            return false;
        }
        return isNightMode();
    }

    private void toggleTheme() {
        int newMode = resolveNight() ? Prefs.THEME_DAY : Prefs.THEME_NIGHT;
        Prefs.setThemeMode(this, newMode);
        AppCompatDelegate.setDefaultNightMode(Prefs.toNightMode(newMode));
        Toast.makeText(this,
                newMode == Prefs.THEME_NIGHT ? "已切换到夜间模式" : "已切换到日间模式",
                Toast.LENGTH_SHORT).show();
    }

    private void showThemeDialog() {
        int current = Prefs.getThemeMode(this);
        String[] options = {getString(R.string.theme_day),
                getString(R.string.theme_night),
                getString(R.string.theme_system)};
        int[] values = {Prefs.THEME_DAY, Prefs.THEME_NIGHT, Prefs.THEME_SYSTEM};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_mode)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    Prefs.setThemeMode(this, values[which]);
                    AppCompatDelegate.setDefaultNightMode(Prefs.toNightMode(values[which]));
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAboutDialog() {
        String text = getString(R.string.about_text, "1.0.0");
        new AlertDialog.Builder(this)
                .setTitle(R.string.about)
                .setMessage(text)
                .setPositiveButton("知道了", null)
                .show();
    }

    // ---------- 搜索 ----------

    private void doSearch(String keyword) {
        final String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        currentQuery = q;
        HistoryStore.add(this, q);
        renderHistory();
        setLoading(true);

        executor.execute(() -> {
            try {
                GitHubApi.SearchResult result = GitHubApi.search(q, PER_PAGE);
                mainHandler.post(() -> onSearchSuccess(result));
            } catch (final Exception e) {
                final String msg = e.getMessage() == null || e.getMessage().isEmpty()
                        ? "网络异常，请检查网络连接后重试" : e.getMessage();
                mainHandler.post(() -> onSearchError(msg));
            }
        });
    }

    private void onSearchSuccess(GitHubApi.SearchResult result) {
        setLoading(false);
        adapter.setData(result.items);
        binding.resultSection.setVisibility(View.VISIBLE);
        binding.errorView.setVisibility(View.GONE);

        if (result.items.isEmpty()) {
            binding.recyclerView.setVisibility(View.GONE);
            binding.emptyView.setVisibility(View.VISIBLE);
            binding.emptyText.setText("未找到与 “" + currentQuery + "” 相关的开源仓库\n换个关键词试试");
            binding.resultCountText.setText("没有搜索结果");
            binding.resultSortHint.setVisibility(View.GONE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
            binding.resultCountText.setText("找到约 " + result.totalCount + " 个仓库");
            binding.resultSortHint.setVisibility(View.VISIBLE);
            binding.resultSortHint.setText("已按星标从高到低排序 · 展示前 "
                    + result.items.size() + " 条");
        }
    }

    private void onSearchError(String message) {
        setLoading(false);
        binding.resultSection.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.emptyView.setVisibility(View.GONE);
        binding.errorView.setVisibility(View.VISIBLE);
        binding.errorText.setText(message);
        binding.resultCountText.setText("搜索失败");
        binding.resultSortHint.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.searchButton.setEnabled(!loading);
        if (loading) {
            binding.recyclerView.setVisibility(View.GONE);
            binding.emptyView.setVisibility(View.GONE);
            binding.errorView.setVisibility(View.GONE);
            binding.resultSection.setVisibility(View.VISIBLE);
            binding.resultCountText.setText("正在搜索…");
            binding.resultSortHint.setVisibility(View.GONE);
        }
    }

    // ---------- 历史记录 / 热门推荐 ----------

    private void renderHistory() {
        List<String> history = HistoryStore.all(this);
        binding.historySection.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
        binding.historyChipGroup.removeAllViews();
        for (final String h : history) {
            Chip chip = new Chip(this);
            chip.setText(h);
            chip.setCloseIconResource(R.drawable.ic_close);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                HistoryStore.remove(this, h);
                renderHistory();
            });
            chip.setOnClickListener(v -> {
                binding.searchEditText.setText(h);
                binding.searchEditText.setSelection(h.length());
                doSearch(h);
            });
            binding.historyChipGroup.addView(chip);
        }
    }

    private void setupHotChips() {
        int primary = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorPrimary, 0);
        binding.hotChipGroup.removeAllViews();
        for (final String kw : HOT_KEYWORDS) {
            Chip chip = new Chip(this);
            chip.setText(kw);
            chip.setChipIconResource(R.drawable.ic_trending);
            chip.setChipIconTint(android.content.res.ColorStateList.valueOf(primary));
            chip.setOnClickListener(v -> {
                binding.searchEditText.setText(kw);
                binding.searchEditText.setSelection(kw.length());
                doSearch(kw);
            });
            binding.hotChipGroup.addView(chip);
        }
    }

    // ---------- 列表点击 ----------

    @Override
    public void onRepoClick(Repo repo) {
        // 优先跳转 GitHub 客户端
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl));
        intent.setPackage("com.github.android");
        try {
            startActivity(intent);
            Toast.makeText(this, "已跳转 GitHub 客户端", Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "未检测到 GitHub 客户端，已使用内置浏览器打开",
                    Toast.LENGTH_SHORT).show();
            openInBuiltinBrowser(repo.htmlUrl);
        }
    }

    @Override
    public void onOpenInBrowser(Repo repo) {
        openInBuiltinBrowser(repo.htmlUrl);
    }

    private void openInBuiltinBrowser(String url) {
        startActivity(new Intent(this, BrowserActivity.class)
                .putExtra(BrowserActivity.EXTRA_URL, url));
    }

    // ---------- 其他 ----------

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), granted -> {
                    });
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }
}
