package com.opensourcesearch;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.opensourcesearch.databinding.ActivityBrowserBinding;
import com.opensourcesearch.databinding.DialogTabsBinding;
import com.opensourcesearch.databinding.ItemTabBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置浏览器（WebView）：多标签、前进后退、刷新、下载、页内查找、
 * 桌面/移动 UA、书签、分享、外部打开等常见浏览器功能。
 */
public class BrowserActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    private static final String HOME_URL = "https://github.com";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private ActivityBrowserBinding binding;
    private TabManager.Tab currentTab;
    private boolean isDesktopMode;
    private boolean pageLoading;
    private String currentUrl = "";
    @Nullable
    private ValueCallback<Uri[]> fileUploadCallback;
    private final TabListAdapter tabAdapter = new TabListAdapter();

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (fileUploadCallback == null) {
                    return;
                }
                Uri[] uris = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri u = result.getData().getData();
                    if (u != null) {
                        uris = new Uri[]{u};
                    }
                }
                fileUploadCallback.onReceiveValue(uris);
                fileUploadCallback = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        isDesktopMode = Prefs.isDesktopMode(this);

        String url = getIntent().getStringExtra(EXTRA_URL);
        currentTab = TabManager.ensure(url == null || url.isEmpty() ? HOME_URL : url);

        setupWebView();
        setupUrlBar();
        setupBottomBar();
        setupFindPanel();
        binding.tabsLabel.setText(String.valueOf(TabManager.count()));

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState);
        } else {
            loadUrl(currentTab.url);
        }
        updateNavButtons();
    }

    // ---------- WebView 初始化 ----------

    private void setupWebView() {
        WebSettings ws = binding.webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setTextZoom(100);
        if (isDesktopMode) {
            ws.setUserAgentString(DESKTOP_UA);
        }
        binding.webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageLoading = true;
                binding.progressBar.setVisibility(View.VISIBLE);
                if (!TextUtils.equals(url, binding.urlInput.getText().toString())) {
                    binding.urlInput.setText(url);
                }
                currentUrl = url;
                updateRefreshIcon();
                updateNavButtons();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoading = false;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                updateRefreshIcon();
                updateNavButtons();
                String title = view.getTitle();
                if (!TextUtils.isEmpty(title)) {
                    TabManager.update(currentTab.id, url, title);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    return false;
                }
                // mailto: / tel: 等交由系统应用处理
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    Toast.makeText(BrowserActivity.this, "无法打开该链接",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    pageLoading = false;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefresh.setRefreshing(false);
                    updateRefreshIcon();
                    Toast.makeText(BrowserActivity.this, "网页加载失败，请检查网络",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        binding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    binding.progressBar.setVisibility(View.GONE);
                } else {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.progressBar.setProgress(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (!TextUtils.isEmpty(title)) {
                    TabManager.update(currentTab.id, view.getUrl(), title);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                // target=_blank：放入新标签页
                final WebView temp = new WebView(BrowserActivity.this);
                temp.getSettings().setJavaScriptEnabled(true);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView v, String url, Bitmap favicon) {
                        if (!TextUtils.isEmpty(url) && !"about:blank".equals(url)) {
                            TabManager.Tab t = TabManager.create(url);
                            temp.destroy();
                            startActivity(new Intent(BrowserActivity.this, BrowserActivity.class)
                                    .putExtra(EXTRA_URL, t.url));
                        }
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;
                try {
                    fileChooserLauncher.launch(fileChooserParams.createIntent());
                } catch (Exception e) {
                    fileUploadCallback = null;
                    Toast.makeText(BrowserActivity.this, "无法打开文件选择器",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        binding.webView.setFindListener(
                (activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
                    if (numberOfMatches > 0) {
                        binding.findCount.setText((activeMatchOrdinal + 1) + "/" + numberOfMatches);
                    } else {
                        binding.findCount.setText("0/0");
                    }
                });

        binding.webView.setDownloadListener((url, userAgent, contentDisposition,
                                             mimeType, contentLength) ->
                downloadFile(url, userAgent, contentDisposition, mimeType));

        binding.swipeRefresh.setOnRefreshListener(() -> binding.webView.reload());
    }

    // ---------- 地址栏 / 加载 ----------

    private void setupUrlBar() {
        binding.btnGo.setOnClickListener(v -> {
            String input = binding.urlInput.getText().toString().trim();
            if (!TextUtils.isEmpty(input)) {
                loadUrl(input);
            }
        });
        binding.urlInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO;
            boolean enter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (go || enter) {
                String input = binding.urlInput.getText().toString().trim();
                if (!TextUtils.isEmpty(input)) {
                    loadUrl(input);
                }
                return true;
            }
            return false;
        });
    }

    private void loadUrl(String input) {
        String url = input == null ? "" : input.trim();
        if (url.isEmpty()) {
            url = HOME_URL;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                url = "https://github.com/search?q=" + Uri.encode(url);
            }
        }
        hideKeyboard();
        binding.webView.loadUrl(url);
    }

    // ---------- 底部工具栏 ----------

    private void setupBottomBar() {
        binding.btnBack.setOnClickListener(v -> goBack());
        binding.btnForward.setOnClickListener(v -> {
            if (binding.webView.canGoForward()) {
                binding.webView.goForward();
            }
        });
        binding.btnRefresh.setOnClickListener(v -> {
            if (pageLoading) {
                binding.webView.stopLoading();
            } else {
                binding.webView.reload();
            }
        });
        binding.btnHome.setOnClickListener(v -> finish());
        binding.btnTabs.setOnClickListener(v -> showTabsDialog());
        binding.btnMenu.setOnClickListener(this::showBrowserMenu);
    }

    private void goBack() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack();
        } else {
            finish();
        }
    }

    private void updateNavButtons() {
        boolean canBack = binding.webView.canGoBack();
        boolean canForward = binding.webView.canGoForward();
        binding.btnBack.setEnabled(canBack);
        binding.btnForward.setEnabled(canForward);
        binding.btnBack.setAlpha(canBack ? 1f : 0.4f);
        binding.btnForward.setAlpha(canForward ? 1f : 0.4f);
    }

    private void updateRefreshIcon() {
        binding.iconRefresh.setImageResource(
                pageLoading ? R.drawable.ic_close : R.drawable.ic_refresh);
    }

    // ---------- 多标签 ----------

    private void showTabsDialog() {
        DialogTabsBinding dialogBinding = DialogTabsBinding.inflate(LayoutInflater.from(this));
        dialogBinding.tabRecyclerView.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(this));
        dialogBinding.tabRecyclerView.setAdapter(tabAdapter);
        dialogBinding.tabEmptyText.setVisibility(
                TabManager.count() == 0 ? View.VISIBLE : View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.tabs_title) + "（" + TabManager.count() + "）")
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.menu_new_tab, (d, which) -> {
                    TabManager.Tab t = TabManager.create(HOME_URL);
                    startActivity(new Intent(this, BrowserActivity.class)
                            .putExtra(EXTRA_URL, t.url));
                })
                .setNegativeButton("关闭", null)
                .create();

        tabAdapter.setListener(new TabListAdapter.Listener() {
            @Override
            public void onTabClick(TabManager.Tab tab) {
                dialog.dismiss();
                if (tab.id == currentTab.id) {
                    return;
                }
                startActivity(new Intent(BrowserActivity.this, BrowserActivity.class)
                        .putExtra(EXTRA_URL, tab.url));
                finish();
            }

            @Override
            public void onTabClose(TabManager.Tab tab) {
                TabManager.close(tab.id);
                binding.tabsLabel.setText(String.valueOf(TabManager.count()));
                if (tab.id == currentTab.id) {
                    dialog.dismiss();
                    finish();
                } else {
                    tabAdapter.submit(TabManager.all());
                    dialog.setTitle(getString(R.string.tabs_title)
                            + "（" + TabManager.count() + "）");
                    dialogBinding.tabEmptyText.setVisibility(
                            TabManager.count() == 0 ? View.VISIBLE : View.GONE);
                }
            }
        });
        tabAdapter.submit(TabManager.all());
        dialog.show();
    }

    // ---------- 浏览器菜单 ----------

    private void showBrowserMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.menu_new_tab);
        popup.getMenu().add(0, 2, 0, R.string.menu_find);
        MenuItem desktopItem = popup.getMenu().add(0, 3, 0, R.string.menu_desktop);
        desktopItem.setCheckable(true);
        desktopItem.setChecked(isDesktopMode);
        boolean bookmarked = BookmarkStore.contains(this, currentUrl);
        popup.getMenu().add(0, 4, 0,
                bookmarked ? R.string.menu_bookmark_remove : R.string.menu_bookmark_add);
        popup.getMenu().add(0, 5, 0, R.string.menu_bookmarks);
        popup.getMenu().add(0, 6, 0, R.string.menu_copy);
        popup.getMenu().add(0, 7, 0, R.string.menu_external);
        popup.getMenu().add(0, 8, 0, R.string.menu_share);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                TabManager.Tab t = TabManager.create(HOME_URL);
                startActivity(new Intent(this, BrowserActivity.class)
                        .putExtra(EXTRA_URL, t.url));
            } else if (id == 2) {
                showFindPanel();
            } else if (id == 3) {
                isDesktopMode = !isDesktopMode;
                Prefs.setDesktopMode(this, isDesktopMode);
                binding.webView.getSettings().setUserAgentString(
                        isDesktopMode ? DESKTOP_UA : null);
                binding.webView.reload();
                Toast.makeText(this, isDesktopMode ? "已切换为桌面版浏览" : "已恢复移动版浏览",
                        Toast.LENGTH_SHORT).show();
            } else if (id == 4) {
                if (bookmarked) {
                    BookmarkStore.remove(this, currentUrl);
                    Toast.makeText(this, "已移除书签", Toast.LENGTH_SHORT).show();
                } else {
                    String title = binding.webView.getTitle();
                    BookmarkStore.add(this,
                            TextUtils.isEmpty(title) ? currentUrl : title, currentUrl);
                    Toast.makeText(this, "已加入书签", Toast.LENGTH_SHORT).show();
                }
            } else if (id == 5) {
                showBookmarksDialog();
            } else if (id == 6) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("url", currentUrl));
                    Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show();
                }
            } else if (id == 7) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)));
                } catch (Exception e) {
                    Toast.makeText(this, "未找到可用的系统浏览器", Toast.LENGTH_SHORT).show();
                }
            } else if (id == 8) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT,
                        (TextUtils.isEmpty(binding.webView.getTitle()) ? currentUrl
                                : binding.webView.getTitle()) + "\n" + currentUrl);
                startActivity(Intent.createChooser(share, getString(R.string.menu_share)));
            }
            return true;
        });
        popup.show();
    }

    private void showBookmarksDialog() {
        List<BookmarkStore.Bookmark> bookmarks = BookmarkStore.all(this);
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "暂无书签", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[bookmarks.size()];
        for (int i = 0; i < bookmarks.size(); i++) {
            titles[i] = bookmarks.get(i).title + "\n" + bookmarks.get(i).url;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_bookmarks)
                .setItems(titles, (d, which) -> loadUrl(bookmarks.get(which).url))
                .setNegativeButton("清空", (d, which) -> {
                    BookmarkStore.clear(this);
                    Toast.makeText(this, "已清空书签", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("关闭", null)
                .show();
    }

    // ---------- 页内查找 ----------

    private void setupFindPanel() {
        binding.btnFindClose.setOnClickListener(v -> hideFindPanel());
        binding.btnFindPrev.setOnClickListener(v -> binding.webView.findNext(false));
        binding.btnFindNext.setOnClickListener(v -> binding.webView.findNext(true));
        binding.findInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString();
                if (q.isEmpty()) {
                    binding.webView.clearMatches();
                    binding.findCount.setText("");
                } else {
                    binding.webView.findAllAsync(q);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void showFindPanel() {
        binding.findPanel.setVisibility(View.VISIBLE);
        binding.findInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.findInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideFindPanel() {
        binding.findPanel.setVisibility(View.GONE);
        binding.webView.clearMatches();
        hideKeyboard();
    }

    // ---------- 下载 ----------

    private void downloadFile(String url, String userAgent, String contentDisposition,
                              String mimeType) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription(currentUrl);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, fileName);
            request.addRequestHeader("User-Agent", userAgent);
            request.setMimeType(mimeType);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(this, "开始下载：" + fileName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "下载失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- 生命周期 ----------

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (binding.findPanel.getVisibility() == View.VISIBLE) {
            hideFindPanel();
            return;
        }
        if (binding.webView.canGoBack()) {
            binding.webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        binding.webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.webView.onResume();
        binding.tabsLabel.setText(String.valueOf(TabManager.count()));
    }

    @Override
    protected void onPause() {
        binding.webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        TabManager.close(currentTab.id);
        super.onDestroy();
    }

    // ---------- 标签列表适配器 ----------

    static class TabListAdapter extends RecyclerView.Adapter<TabListAdapter.ViewHolder> {

        interface Listener {
            void onTabClick(TabManager.Tab tab);

            void onTabClose(TabManager.Tab tab);
        }

        private final List<TabManager.Tab> items = new ArrayList<>();
        @Nullable
        private Listener listener;

        void submit(List<TabManager.Tab> tabs) {
            items.clear();
            if (tabs != null) {
                items.addAll(tabs);
            }
            notifyDataSetChanged();
        }

        void setListener(@Nullable Listener l) {
            this.listener = l;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            ItemTabBinding b = ItemTabBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TabManager.Tab tab = items.get(position);
            holder.binding.tabTitle.setText(
                    TextUtils.isEmpty(tab.title) ? "新标签页" : tab.title);
            holder.binding.tabUrl.setText(tab.url);
            holder.binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTabClick(tab);
                }
            });
            holder.binding.tabClose.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTabClose(tab);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemTabBinding binding;

            ViewHolder(ItemTabBinding b) {
                super(b.getRoot());
                binding = b;
            }
        }
    }
}
