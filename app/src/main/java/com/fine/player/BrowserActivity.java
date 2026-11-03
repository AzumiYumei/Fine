package com.fine.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Typeface;
import java.util.ArrayList;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONTokener;
import java.util.List;

@androidx.media3.common.util.UnstableApi
public final class BrowserActivity extends Activity {
    private WebView web;
    private EditText address;
    private ImageButton back, forward, reload, tabsButton;
    private TextView resourcesButton;
    private LinearLayout addressBar;
    private FrameLayout viewport;
    private LinearLayout homePage;
    private final List<Tab> tabs = new ArrayList<>();
    private Tab current;
    private AlertDialog menuDialog, tabsDialog;
    private static final class Tab {
        WebView view;
        BrowserResources resources = new BrowserResources();
        boolean home = true;
        int generation;
        final java.util.concurrent.atomic.AtomicInteger blocked = new java.util.concurrent.atomic.AtomicInteger();
    }
    private ProgressBar progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BrowserResources resources;
    private boolean active, destroyed;
    private AlertDialog resourceDialog;
    private ResourceCards resourceAdapter;
    private BrowserPrivacy privacy;
    private BrowserHistory history;
    private boolean historyErrorShown;
    private AlertDialog privacyDialog;
    private List<BrowserResources.Entry> shown;
    private boolean mediaOnly = true;
    // Read DOM media and performance entries without exposing a native JavaScript bridge.
    private static final String SCAN = """
        (function(){
          var a=[],capture=CAPTURE_ENABLED,frames=0;
          document.querySelectorAll('video,audio').forEach(function(e){
            if(a.length>=20)return;
            var u=e.currentSrc||e.src;if(!/^https?:/i.test(u))return;
            var source=Array.from(e.querySelectorAll('source')).find(function(s){return s.src===u;});
            var type=e.getAttribute('type')||(source&&source.type)||(e.tagName==='AUDIO'?'audio/unknown':'video/unknown');
            var frame='';
            if(capture&&frames<2&&e.videoWidth>0&&e.readyState>=2){
              try{var c=document.createElement('canvas');c.width=320;c.height=Math.max(1,Math.min(480,Math.round(320*e.videoHeight/e.videoWidth)));
                c.getContext('2d').drawImage(e,0,0,c.width,c.height);frame=c.toDataURL('image/jpeg',0.65);frames++;}catch(ignore){}
            }
            a.push([u,type,Number.isFinite(e.duration)?e.duration:-1,e.duration===Infinity,e.videoWidth||0,e.videoHeight||0,e.title||e.getAttribute('aria-label')||'',frame]);
          });
          performance.getEntriesByType('resource').slice(-500).forEach(function(e){a.push([e.name,'']);});
          return JSON.stringify(a);
        })()
        """;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active || destroyed) return;
            Tab tab = current;
            int token = tab.generation;
            if (tab.home || privacy.enabled("disable_js", false)) { refreshResources(); handler.postDelayed(this, 1500); return; }
            // Never send the native request inventory (including cross-origin frame URLs) into page JavaScript.
            boolean capture = resourceDialog != null && resourceDialog.isShowing();
            web.evaluateJavascript(SCAN.replace("CAPTURE_ENABLED", Boolean.toString(capture)), value -> {
                if (destroyed || tab != current || token != tab.generation) return;
                try {
                    Object decoded = new JSONTokener(value).nextValue();
                    if (!(decoded instanceof String)) return;
                    JSONArray array = new JSONArray((String) decoded);
                    for (int i = 0; i < Math.min(array.length(), 600); i++) {
                        JSONArray item = array.getJSONArray(i);
                        String link = item.getString(0);
                        if (privacy.blockTrackers && BrowserPrivacy.knownTracker(link)) continue;
                        resources.add(link, item.optString(1));
                        if (item.length() > 2 && isWebUrl(link)) {
                            MediaPreviews.Info info = MediaPreviews.info(link);
                            double seconds = item.optDouble(2, -1);
                            if (seconds >= 0 && seconds < 315360000) info.duration = (long)(seconds * 1000);
                            info.live = item.optBoolean(3, false);
                            info.width = Math.max(0, Math.min(16384, item.optInt(4)));
                            info.height = Math.max(0, Math.min(16384, item.optInt(5)));
                            String title = item.optString(6);
                            if (!title.isEmpty()) info.title = title.substring(0, Math.min(120, title.length()));
                            String frame = item.optString(7);
                            if (frame.startsWith("data:image/jpeg;base64,") && frame.length() < 150000 && MediaPreviews.image(link) == null) {
                                byte[] bytes = android.util.Base64.decode(frame.substring(23), android.util.Base64.DEFAULT);
                                android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                                if (bounds.outWidth <= 640 && bounds.outHeight <= 480) MediaPreviews.image(link, android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                            }
                        }
                    }
                } catch (Exception ignored) { /* Pages may disable or replace performance APIs. */ }
                refreshResources();
            });
            refreshResources();
            handler.postDelayed(this, 1500);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
        super.onCreate(state);
        privacy = new BrowserPrivacy(this);
        history = BrowserHistory.get(this);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT >= 26)
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root = column();
        root.setBackgroundColor(Color.WHITE);
        root.setFocusableInTouchMode(true);
        viewport = new FrameLayout(this);
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        homePage = column();
        homePage.setGravity(Gravity.CENTER);
        homePage.setPadding(dp(28), 0, dp(28), dp(100));
        homePage.setBackgroundColor(Color.WHITE);
        TextView brand = new TextView(this);
        brand.setText("Fine"); brand.setTextSize(46); brand.setTextColor(QuietStyle.INK);
        brand.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        brand.setGravity(Gravity.CENTER);
        homePage.addView(brand, new LinearLayout.LayoutParams(-1, dp(92)));
        LinearLayout homeSearch = new LinearLayout(this);
        homeSearch.setGravity(Gravity.CENTER_VERTICAL);
        homeSearch.setBackground(QuietStyle.panel(this, QuietStyle.PAPER, 0));
        homeSearch.addView(icon("search", "搜索", v -> editAddress()), new LinearLayout.LayoutParams(dp(48), dp(52)));
        TextView hint = new TextView(this); hint.setText("搜索或输入网址"); hint.setTextSize(15); hint.setTextColor(QuietStyle.MUTED);
        homeSearch.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
        homeSearch.setOnClickListener(v -> editAddress());
        homeSearch.setContentDescription("搜索或输入网址");
        homePage.addView(homeSearch, new LinearLayout.LayoutParams(-1, dp(52)));
        viewport.addView(homePage, new FrameLayout.LayoutParams(-1, -1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(QuietStyle.ACCENT));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
        addressBar = new LinearLayout(this);
        addressBar.setGravity(Gravity.CENTER_VERTICAL);
        addressBar.setPadding(dp(12), dp(4), dp(8), dp(4));
        address = new EditText(this);
        address.setId(R.id.browser_address);
        address.setSingleLine(true); address.setSelectAllOnFocus(true);
        address.setTextSize(14); address.setHint("搜索或输入网址");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        QuietStyle.input(address);
        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_UP)) {
                navigate(address.getText().toString()); return true;
            }
            return false;
        });
        address.setOnFocusChangeListener((v, focused) -> {
            if (reload != null) { reload.setImageDrawable(new BrowserIcon(focused ? "go" : "reload")); reload.setContentDescription(focused ? "访问网址" : "刷新"); }
        });
        addressBar.addView(address, new LinearLayout.LayoutParams(0, dp(44), 1));
        resourcesButton = new TextView(this);
        resourcesButton.setId(R.id.browser_resources);
        resourcesButton.setTextSize(12); resourcesButton.setTextColor(QuietStyle.ACCENT);
        resourcesButton.setGravity(Gravity.CENTER); resourcesButton.setOnClickListener(v -> showResources());
        resourcesButton.setBackground(QuietStyle.panel(this, QuietStyle.SOFT, 0));
        LinearLayout.LayoutParams badge = new LinearLayout.LayoutParams(dp(48), dp(44)); badge.leftMargin = dp(6);
        addressBar.addView(resourcesButton, badge);
        reload = icon("reload", "刷新", v -> {
            if (address.hasFocus()) navigate(address.getText().toString());
            else if (web.getProgress() < 100) { web.stopLoading(); progress.setVisibility(View.INVISIBLE); reload.setImageDrawable(new BrowserIcon("reload")); }
            else web.reload();
        });
        addressBar.addView(reload, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(addressBar);
        View divider = new View(this); divider.setBackgroundColor(0xffeeeeee);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setId(R.id.browser_toolbar);
        back = icon("back", "后退", v -> goBack());
        forward = icon("forward", "前进", v -> { if (web.canGoForward()) { current.home = false; web.goForward(); updateChrome(); } });
        ImageButton home = icon("home", "主页", v -> showHome()); home.setId(R.id.browser_home);
        tabsButton = icon("tabs:1", "标签页", v -> showTabs()); tabsButton.setId(R.id.browser_tabs);
        ImageButton menu = icon("menu", "菜单", v -> showMenu()); menu.setId(R.id.browser_menu);
        for (View item : new View[]{back, forward, home, tabsButton, menu}) navigation.addView(item, new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(navigation);
        setContentView(root);
        int restoredCount = state == null ? 0 : Math.min(state.getInt("tab_count", 0), 8);
        for (int i = 0; i < restoredCount; i++) {
            Tab tab = createTab();
            Bundle saved = state.getBundle("tab_" + i);
            if (saved != null) { tab.view.restoreState(saved); tab.home = saved.getBoolean("home", true); }
        }
        if (tabs.isEmpty()) createTab();
        selectTab(tabs.get(state == null ? 0 : Math.max(0, Math.min(state.getInt("selected_tab", 0), tabs.size()-1))));
        String incoming = incomingUrl(getIntent());
        if (state == null && !incoming.isEmpty()) navigate(incoming);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private Tab createTab() {
        Tab tab = new Tab();
        tab.view = new WebView(this);
        WebView web = tab.view;
        tabs.add(tab);
        web.setId(R.id.browser_web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        privacy.apply(web);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String title) { recordHistory(view.getUrl(), title, false); }
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) { callback.invoke(origin, false, false); }
            @Override public void onProgressChanged(WebView view, int value) {
                if (tab != current || tab.home) return;
                progress.setProgress(value); progress.setVisibility(value == 100 ? View.INVISIBLE : View.VISIBLE);
                if (!address.hasFocus()) { reload.setImageDrawable(new BrowserIcon(value == 100 ? "reload" : "stop")); reload.setContentDescription(value == 100 ? "刷新" : "停止加载"); }
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                recordHistory(url, null, true);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isWebUrl(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !isWebUrl(url); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!request.isForMainFrame() && privacy.blockTrackers && BrowserPrivacy.knownTracker(url)) {
                    tab.blocked.incrementAndGet();
                    return new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
                }
                tab.resources.add(url, "");
                return null;
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap icon) {
                tab.generation++;
                tab.resources.clear(); tab.resources.add(url, "");
                if (tab != current) return;
                if (!address.hasFocus()) address.setText(isWebUrl(url) ? url : "");
                refreshResources();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (url.equals(view.getUrl())) recordHistory(url, view.getTitle(), false);
                if (tab != current) return;
                if (!address.hasFocus()) address.setText(isWebUrl(url) ? url : "");
                refreshResources();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (tab == current && request.isForMainFrame()) Toast.makeText(BrowserActivity.this, "网页加载失败，请检查网址或网络后刷新", Toast.LENGTH_LONG).show();
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, length) -> {
            tab.resources.add(url, mime); if (tab == current) showResources();
        });
        return tab;
    }
    private void selectTab(Tab tab) {
        if (current != null) { current.view.onPause(); viewport.removeView(current.view); }
        if (resourceDialog != null) resourceDialog.dismiss();
        current = tab; web = tab.view; resources = tab.resources;
        viewport.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
        if (active) web.onResume();
        address.clearFocus();
        address.setText(isWebUrl(web.getUrl()) ? web.getUrl() : "");
        updateChrome();
    }
    private void updateChrome() {
        homePage.setVisibility(current.home ? View.VISIBLE : View.GONE);
        web.setVisibility(current.home ? View.INVISIBLE : View.VISIBLE);
        addressBar.setVisibility(current.home && !address.hasFocus() ? View.GONE : View.VISIBLE);
        progress.setVisibility(!current.home && web.getProgress() < 100 ? View.VISIBLE : View.INVISIBLE);
        tabsButton.setImageDrawable(new BrowserIcon("tabs:" + tabs.size()));
        tabsButton.setContentDescription("标签页，当前共 " + tabs.size() + " 个");
        refreshResources();
    }
    private void editAddress() {
        addressBar.setVisibility(View.VISIBLE);
        address.setText(current.home ? "" : (isWebUrl(web.getUrl()) ? web.getUrl() : ""));
        address.requestFocus(); address.selectAll();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(address, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }
    private void showHome() {
        web.stopLoading(); current.home = true; hideKeyboard(); updateChrome();
    }
    private void hideKeyboard() {
        address.clearFocus(); web.requestFocus();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
    }
    private void goBack() {
        if (address.hasFocus()) { hideKeyboard(); updateChrome(); }
        else if (current.home && isWebUrl(web.getUrl())) { current.home = false; updateChrome(); }
        else if (!current.home && web.canGoBack()) web.goBack();
        else if (!current.home) showHome();
        else if (tabs.size() > 1) closeTab(current);
        else finish();
    }
    private void closeTab(Tab tab) {
        int index = tabs.indexOf(tab);
        if (tab == current) { viewport.removeView(tab.view); current = null; }
        tabs.remove(tab); tab.view.stopLoading(); tab.view.destroy();
        if (tabs.isEmpty()) createTab();
        if (current == null) selectTab(tabs.get(Math.min(index, tabs.size()-1))); else updateChrome();
    }
    private void showTabs() {
        LinearLayout content = sheetContent("标签页");
        ScrollView scroll = new ScrollView(this); LinearLayout rows = column(); scroll.addView(rows);
        for (Tab tab : tabs) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(this);
            String title = tab.home ? "主页" : tab.view.getTitle();
            label.setText((tab == current ? "●  " : "    ") + (title == null ? tab.view.getUrl() : title));
            label.setTextSize(15); label.setTextColor(tab == current ? QuietStyle.ACCENT : QuietStyle.INK);
            label.setMaxLines(2); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setOnClickListener(v -> { tabsDialog.dismiss(); selectTab(tab); });
            row.addView(label, new LinearLayout.LayoutParams(0, dp(56), 1));
            row.addView(icon("stop", "关闭标签页", v -> { tabsDialog.dismiss(); closeTab(tab); showTabs(); }), new LinearLayout.LayoutParams(dp(48), dp(48)));
            rows.addView(row);
        }
        content.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(dp(320), tabs.size()*dp(56))));
        Button add = button("＋ 新建标签页", v -> {
            if (tabs.size() >= 8) { Toast.makeText(this, "最多打开 8 个标签页", Toast.LENGTH_SHORT).show(); return; }
            tabsDialog.dismiss(); selectTab(createTab());
        });
        content.addView(add, new LinearLayout.LayoutParams(-1, dp(48)));
        tabsDialog = new AlertDialog.Builder(this).setView(content).create(); showBottom(tabsDialog);
    }
    private void showMenu() {
        LinearLayout content = sheetContent("菜单");
        String[] labels = {"资源嗅探", "下载", "已下载", "复制网址", "刷新", "设置"};
        String[] icons = {"media", "download", "media", "copy", "reload", "settings"};
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 3; c++) {
                final int index = r*3+c;
                LinearLayout cell = column(); cell.setGravity(Gravity.CENTER);
                ImageButton image = icon(icons[index], labels[index], v -> menuAction(index));
                cell.addView(image, new LinearLayout.LayoutParams(dp(48), dp(46)));
                TextView label = new TextView(this); label.setText(labels[index]); label.setTextSize(13); label.setTextColor(QuietStyle.INK);
                cell.addView(label); cell.setOnClickListener(v -> menuAction(index));
                row.addView(cell, new LinearLayout.LayoutParams(0, dp(84), 1));
            }
            content.addView(row);
        }
        LinearLayout more = new LinearLayout(this);
        Button historyButton = button("历史记录", v -> { menuDialog.dismiss(); startActivity(new Intent(this, HistoryActivity.class)); });
        historyButton.setId(R.id.browser_history);
        more.addView(historyButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        more.addView(button("隐私保护", v -> { menuDialog.dismiss(); showPrivacy(); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(more);
        menuDialog = new AlertDialog.Builder(this).setView(content).create(); showBottom(menuDialog);
    }
    private void menuAction(int index) {
        menuDialog.dismiss();
        if (index == 0) showResources();
        else if (index == 3) {
            if (isWebUrl(web.getUrl()) && !current.home) {
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("网址", web.getUrl()));
                Toast.makeText(this, "已复制网址", Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this, "请先打开网页", Toast.LENGTH_SHORT).show();
        } else if (index == 4) { if (!current.home) web.reload(); }
        else startActivity(new Intent(this, MainActivity.class).putExtra("page", index == 1 ? 0 : index == 2 ? 1 : 2));
    }
    private void recordHistory(String url, String title, boolean visit) {
        if (!isWebUrl(url) || !history.enabled()) return;
        long time = System.currentTimeMillis();
        BrowserHistory.WORKER.execute(() -> {
            try { if (visit) history.visit(url, time); else history.title(url, title); }
            catch (RuntimeException e) { handler.post(() -> {
                if (!destroyed && !historyErrorShown) { historyErrorShown = true; Toast.makeText(this, "历史记录保存失败，请检查可用空间", Toast.LENGTH_LONG).show(); }
            }); }
        });
    }
    private LinearLayout sheetContent(String title) {
        LinearLayout content = column(); content.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView heading = new TextView(this); heading.setText(title); heading.setTextSize(18); heading.setTextColor(QuietStyle.INK);
        heading.setPadding(0, 0, 0, dp(12)); content.addView(heading); return content;
    }
    private void showBottom(AlertDialog dialog) {
        dialog.show(); Window window = dialog.getWindow();
        if (window != null) { window.setBackgroundDrawable(QuietStyle.panel(this, Color.WHITE, 0)); window.setGravity(Gravity.BOTTOM); window.setLayout(-1, -2); }
    }
    static boolean isWebUrl(String value) {
        try { Links.normalize(value); return true; } catch (Exception e) { return false; }
    }
    static String incomingUrl(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            String url = intent.getDataString(); return isWebUrl(url) ? url : "";
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) return BrowserResources.sharedUrl(intent.getStringExtra(Intent.EXTRA_TEXT));
        return "";
    }
    private void navigate(String input) {
        try {
            String url = BrowserResources.address(input);
            hideKeyboard(); current.home = false;
            address.setText(url); updateChrome(); web.loadUrl(url);
        } catch (IllegalArgumentException e) { address.setError(e.getMessage()); }
    }
    private void refreshResources() {
        back.setEnabled(!current.home || isWebUrl(web.getUrl()) || tabs.size() > 1);
        forward.setEnabled(web.canGoForward());
        int count = resources.snapshot(true).size();
        resourcesButton.setText("媒体\n" + count);
        resourcesButton.setContentDescription("资源嗅探，发现 " + count + " 个媒体");
        resourcesButton.setVisibility(count > 0 && !current.home ? View.VISIBLE : View.GONE);
        if (resourceDialog == null || !resourceDialog.isShowing()) return;
        shown = resources.snapshot(mediaOnly);
        resourceAdapter.update(shown);
    }
    private void showResources() {
        if (resourceDialog != null && resourceDialog.isShowing()) { refreshResources(); return; }
        LinearLayout content = column();
        content.setPadding(dp(16), 0, dp(16), 0);
        TextView help = new TextView(this);
        help.setText("点按左侧画面预览；读取成功后显示封面与时长。无封面时不会自动下载视频。");
        help.setTextColor(QuietStyle.MUTED); help.setPadding(0, dp(8), 0, dp(12));
        content.addView(help);
        Switch filter = new Switch(this); filter.setText("仅完整媒体（隐藏分片）"); filter.setChecked(mediaOnly);
        content.addView(filter);
        ListView list = new ListView(this);
        resourceAdapter = new ResourceCards(this, this::preview);
        list.setAdapter(resourceAdapter);
        TextView empty = new TextView(this); empty.setText("暂无资源，请在网页中播放视频或切换到全部资源。");
        content.addView(empty); list.setEmptyView(empty);
        content.addView(list, new LinearLayout.LayoutParams(-1, dp(300)));
        list.setOnItemClickListener((parent, view, position, id) -> resourceActions(resourceAdapter.getItem(position)));
        filter.setOnCheckedChangeListener((button, checked) -> { mediaOnly = checked; refreshResources(); });
        resourceDialog = new AlertDialog.Builder(this).setTitle("资源嗅探").setView(content)
                .setNeutralButton("清空", (d, w) -> { current.generation++; resources.clear(); web.evaluateJavascript("performance.clearResourceTimings()", null); refreshResources(); })
                .setPositiveButton("返回网页", null).create();
        showBottom(resourceDialog); refreshResources();
    }
    private void resourceActions(BrowserResources.Entry entry) {
        boolean hls = entry.type.equals("HLS");
        String pageTitle = web.getTitle();
        LinearLayout content = sheetContent(MediaPreviews.name(entry));
        if (MediaPreviews.canPreview(entry)) {
            Button preview = button("▶ 预览 · " + MediaPreviews.duration(MediaPreviews.info(entry.url)), v -> preview(entry));
            content.addView(preview, new LinearLayout.LayoutParams(-1, dp(48)));
        }
        TextView link = new TextView(this); link.setText(entry.url); link.setTextSize(12); link.setTextColor(QuietStyle.MUTED);
        link.setMaxLines(3); link.setEllipsize(android.text.TextUtils.TruncateAt.END);
        link.setPadding(0, 0, 0, dp(16)); content.addView(link);
        AlertDialog actions = new AlertDialog.Builder(this).setView(content).create();
        if (hls) {
            LinearLayout primary = new LinearLayout(this);
            Button play = button("播放", v -> { actions.dismiss(); startActivity(PlayerActivity.online(this, entry.url, pageTitle)); });
            Button download = button("下载 M3U8", v -> { actions.dismiss(); sendToDownload(entry.url, pageTitle, true); });
            QuietStyle.button(download, QuietStyle.ACCENT, Color.WHITE);
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(48), 1); half.setMargins(dp(4), 0, dp(4), dp(8));
            primary.addView(play, half); primary.addView(download, half); content.addView(primary);
        } else content.addView(button("按 M3U8 尝试解析", v -> { actions.dismiss(); sendToDownload(entry.url, pageTitle, false); }));
        content.addView(button("复制链接", v -> {
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("资源链接", entry.url));
            Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show(); actions.dismiss();
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        showBottom(actions);
    }
    private void preview(BrowserResources.Entry entry) {
        // A user-initiated preview is the only native network probe; no bulk video downloads.
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(e){e.pause();})", null);
        startActivity(MediaPreviewActivity.intent(this, entry));
    }
    private void showPrivacy() {
        LinearLayout content = sheetContent("隐私保护");
        TextView info = new TextView(this);
        info.setText("已禁止网站访问定位、摄像头、麦克风及本地文件。\n本标签页已拦截 " + current.blocked.get() + " 次名单内请求。");
        info.setTextColor(QuietStyle.MUTED); content.addView(info);
        privacySwitch(content, "阻止第三方 Cookie", "third_party", true);
        privacySwitch(content, "拦截常见统计请求（有限名单）", "trackers", true);
        privacySwitch(content, "禁用 JavaScript（可能影响视频和登录）", "disable_js", false);
        TextView limits = new TextView(this);
        limits.setText("这不是匿名模式：网站仍能看到 IP、登录身份及你提交的内容，浏览器指纹也无法完全隐藏。第一方统计和未列入名单的跟踪可能仍会发生。修改后刷新网页生效。");
        limits.setTextColor(QuietStyle.MUTED); limits.setPadding(0, dp(12), 0, dp(12)); content.addView(limits);
        content.addView(button("清除网站数据并关闭标签页", v -> new AlertDialog.Builder(this)
                .setTitle("清除网站数据？").setMessage("将退出网站登录，关闭所有标签页，并清除 Cookie、网站存储和网页缓存。已下载视频保留。")
                .setNegativeButton("取消", null).setPositiveButton("清除", (d, w) -> {
                    privacyDialog.dismiss();
                    web.clearCache(true);
                    for (Tab tab : new ArrayList<>(tabs)) closeTab(tab);
                    WebStorage.getInstance().deleteAllData(); MediaPreviews.clear();
                    CookieManager.getInstance().removeAllCookies(done -> Toast.makeText(this, "网站数据已清除", Toast.LENGTH_SHORT).show());
                }).show()), new LinearLayout.LayoutParams(-1, dp(48)));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        privacyDialog = new AlertDialog.Builder(this).setView(scroll).setPositiveButton("完成并刷新", (d, w) -> { if (!current.home) web.reload(); }).create();
        showBottom(privacyDialog);
    }
    private void privacySwitch(LinearLayout parent, String title, String key, boolean fallback) {
        Switch toggle = new Switch(this); toggle.setText(title); toggle.setTextSize(14); toggle.setChecked(privacy.enabled(key, fallback));
        toggle.setPadding(0, dp(12), 0, dp(12)); parent.addView(toggle);
        toggle.setOnCheckedChangeListener((button, checked) -> { privacy.set(key, checked); for (Tab tab : tabs) privacy.apply(tab.view); });
    }
    private void sendToDownload(String url, String title, boolean start) {
        startActivity(new Intent(this, MainActivity.class).putExtra("sniffed_url", url).putExtra("sniffed_title", title)
                .putExtra("start_download", start).putExtra("page", 0));
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        String url = incomingUrl(intent); if (!url.isEmpty()) navigate(url);
    }
    @Override public void onBackPressed() { goBack(); }
    @Override protected void onResume() { super.onResume(); active = true; web.resumeTimers(); web.onResume(); handler.post(tick); }
    @Override protected void onPause() { active = false; handler.removeCallbacks(tick); web.onPause(); web.pauseTimers(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab_count", tabs.size()); state.putInt("selected_tab", tabs.indexOf(current));
        for (int i = 0; i < tabs.size(); i++) { Bundle saved = new Bundle(); tabs.get(i).view.saveState(saved); saved.putBoolean("home", tabs.get(i).home); state.putBundle("tab_" + i, saved); }
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        destroyed = true; handler.removeCallbacksAndMessages(null);
        if (resourceDialog != null) resourceDialog.dismiss();
        if (privacyDialog != null) privacyDialog.dismiss();
        if (menuDialog != null) menuDialog.dismiss(); if (tabsDialog != null) tabsDialog.dismiss();
        viewport.removeAllViews();
        for (Tab tab : tabs) { tab.view.stopLoading(); tab.view.destroy(); }
        super.onDestroy();
    }
    private ImageButton icon(String name, String description, View.OnClickListener click) {
        ImageButton button = new ImageButton(this); button.setImageDrawable(new BrowserIcon(name));
        button.setContentDescription(description); button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(13), dp(13), dp(13), dp(13));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x11777777), null, QuietStyle.panel(this, Color.WHITE, 0)));
        button.setOnClickListener(click); return button;
    }
    private int dp(int n) { return QuietStyle.dp(this, n); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private Button button(String text, View.OnClickListener click) {
        Button button = new Button(this); button.setText(text); QuietStyle.button(button, QuietStyle.SOFT, QuietStyle.INK); button.setOnClickListener(click); return button;
    }
}
