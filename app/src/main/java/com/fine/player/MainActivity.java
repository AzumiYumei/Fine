package com.fine.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.offline.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int ink = Color.rgb(25, 47, 44), green = Color.rgb(8, 127, 112);
    private FineApp app;
    private EditText url, title;
    private TextView status, empty;
    private LinearLayout list;
    private Button downloadButton;
    private DownloadHelper preparing;
    private ScrollView downloadPage, libraryPage;
    private Button downloadTab, libraryTab;
    private int page;
    private boolean visible;
    private String lastRows = "";
    private boolean querying;
    private final Map<String, TextView> rowStatus = new HashMap<>();
    private final Map<String, ProgressBar> rowProgress = new HashMap<>();
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (visible && page == 1) { renderDownloads(); handler.postDelayed(this, 1000); } }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        app = (FineApp) getApplication();
        buildUi();
        url.setText(getPreferences(0).getString("url", ""));
        if (state != null) {
            url.setText(state.getString("url", ""));
            title.setText(state.getString("title", ""));
        }
        showPage(state != null ? state.getInt("page", 0) : getIntent().getIntExtra("page", 0));
        app.downloads();
        DownloadService.sendResumeDownloads(this, VideoDownloadService.class, false);
    }

    private void buildUi() {
        LinearLayout shell = column();
        FrameLayout pages = new FrameLayout(this);
        shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        downloadPage = new ScrollView(this);
        downloadPage.setId(R.id.download_page);
        downloadPage.setFillViewport(true);
        libraryPage = new ScrollView(this);
        libraryPage.setId(R.id.library_page);
        libraryPage.setFillViewport(true);
        pages.addView(downloadPage, new FrameLayout.LayoutParams(-1, -1));
        pages.addView(libraryPage, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout tabs = row();
        tabs.setPadding(dp(16), dp(6), dp(16), dp(6));
        tabs.setBackgroundColor(Color.WHITE);
        downloadTab = button("下载", true, v -> showPage(0));
        downloadTab.setId(R.id.download_tab);
        libraryTab = button("我的下载", false, v -> showPage(1));
        libraryTab.setId(R.id.library_tab);
        tabs.addView(downloadTab, weighted());
        tabs.addView(libraryTab, weighted());
        shell.addView(tabs);
        setContentView(shell);
        LinearLayout root = column();
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        downloadPage.addView(root);
        TextView brand = text("FINE / 轻播", 13, green);
        brand.setTypeface(null, Typeface.BOLD);
        root.addView(brand);
        TextView heading = text("下载视频", 28, ink);
        heading.setTypeface(null, Typeface.BOLD);
        add(root, heading, 10);
        add(root, text("粘贴视频链接，下载或直接播放", 14, Color.GRAY), 8);

        LinearLayout input = card();
        add(root, input, 24);
        input.addView(text("视频链接", 14, ink));
        url = new EditText(this);
        url.setId(R.id.video_url);
        url.setHint("https://example.com/video.m3u8");
        url.setTextSize(14);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setSingleLine(true);
        input.addView(url, new LinearLayout.LayoutParams(-1, dp(52)));
        title = new EditText(this);
        title.setId(R.id.video_title);
        title.setHint("视频名称（可选）");
        title.setTextSize(14);
        title.setSingleLine(true);
        input.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout actions = row();
        actions.addView(button("粘贴", false, v -> paste()), weighted());
        Button play = button("播放", true, v -> playInput());
        play.setId(R.id.play_online);
        actions.addView(play, weighted());
        downloadButton = button("下载", false, v -> download());
        actions.addView(downloadButton, weighted());
        add(input, actions, 12);
        add(input, text("不限速 · 自动重试 3 次 · 使用当前网络", 12, Color.GRAY), 10);
        Button concurrency = button("下载并发：" + app.parallelism() + " 线程", false, null);
        concurrency.setOnClickListener(v -> {
            int[] values = {2, 4, 6, 8};
            new AlertDialog.Builder(this).setTitle("分片并发（下次开始或继续时生效）")
                    .setItems(new String[]{"2 线程 · 较少占用", "4 线程", "6 线程 · 默认", "8 线程"}, (dialog, which) -> {
                        app.parallelism(values[which]); concurrency.setText("下载并发：" + values[which] + " 线程");
                    }).show();
        });
        add(input, concurrency, 6);

        status = text("支持 M3U8 点播与直播在线播放", 13, green);
        add(root, status, 18);
        LinearLayout library = column();
        library.setPadding(dp(22), dp(24), dp(22), dp(24));
        libraryPage.addView(library);
        library.addView(text("FINE / 视频库", 13, green));
        TextView downloads = text("我的下载", 22, ink);
        downloads.setTypeface(null, Typeface.BOLD);
        add(library, downloads, 12);
        add(library, text("管理下载进度，或打开已保存的视频", 13, Color.GRAY), 8);
        list = column();
        add(library, list, 20);
        empty = text("还没有下载\n添加一个视频，把喜欢的内容留在本地。", 14, Color.GRAY);
        empty.setPadding(dp(16), dp(24), dp(16), dp(24));
        list.addView(empty);
    }

    private void paste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0)
            url.setText(clipboard.getPrimaryClip().getItemAt(0).coerceToText(this));
        else message("剪贴板中没有链接");
    }
    private String inputLink() {
        String link = Links.normalize(url.getText().toString());
        getPreferences(0).edit().putString("url", link).apply();
        return link;
    }
    private MediaItem item(String link) {
        return new MediaItem.Builder().setUri(link).setMimeType(MimeTypes.APPLICATION_M3U8).build();
    }
    private void playInput() {
        try {
            startActivity(PlayerActivity.online(this, inputLink(), title.getText().toString().trim()));
        } catch (IllegalArgumentException e) { url.setError(e.getMessage()); }
    }
    private void showPage(int selected) {
        page = selected == 1 ? 1 : 0;
        downloadPage.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        libraryPage.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        downloadTab.setSelected(page == 0);
        libraryTab.setSelected(page == 1);
        downloadTab.setTextColor(page == 0 ? Color.WHITE : green);
        libraryTab.setTextColor(page == 1 ? Color.WHITE : green);
        downloadTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(page == 0 ? green : Color.rgb(230,242,237)));
        libraryTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(page == 1 ? green : Color.rgb(230,242,237)));
        handler.removeCallbacks(refresh);
        if (visible && page == 1) handler.post(refresh);
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(url.getWindowToken(), 0);
    }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent); setIntent(intent); showPage(intent.getIntExtra("page", page));
    }
    @Override public void onBackPressed() {
        if (page == 1) showPage(0); else super.onBackPressed();
    }

    private void download() {
        final String link;
        try { link = inputLink(); }
        catch (IllegalArgumentException e) { url.setError(e.getMessage()); return; }
        if (preparing != null) return;
        try {
            Download existing = app.downloads().getDownloadIndex().getDownload(Links.id(link));
            if (existing != null) { message("此链接已在下载列表中，可继续、重试或离线播放"); showPage(1); return; }
        } catch (IOException e) { message("读取下载记录失败"); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        String enteredTitle = title.getText().toString().trim();
        final String name = enteredTitle.isEmpty() ? "视频 " + new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date()) : enteredTitle;
        downloadButton.setEnabled(false);
        downloadButton.setText("解析中");
        preparing = DownloadHelper.forMediaItem(this, item(link), new DefaultRenderersFactory(this), app.source(false));
        preparing.prepare(new DownloadHelper.Callback() {
            @Override public void onPrepared(DownloadHelper helper) {
                try {
                    DownloadRequest request = helper.getDownloadRequest(Links.id(link), name.getBytes(StandardCharsets.UTF_8));
                    DownloadService.sendAddDownload(MainActivity.this, VideoDownloadService.class, request, false);
                    message("已加入下载队列");
                    showPage(1);
                } finally { finishPreparation(helper); }
            }
            @Override public void onPrepareError(DownloadHelper helper, IOException e) {
                message(e instanceof DownloadHelper.LiveContentUnsupportedException
                        ? "直播可在线播放，暂不支持下载录制" : "无法解析视频，请检查网络、链接是否过期或需要登录");
                finishPreparation(helper);
            }
        });
    }
    private void finishPreparation(DownloadHelper helper) {
        helper.release();
        preparing = null;
        downloadButton.setEnabled(true);
        downloadButton.setText("下载");
    }

    private void renderDownloads() {
        if (querying) return;
        querying = true;
        Map<String, Download> active = new HashMap<>();
        for (Download d : app.downloads().getCurrentDownloads()) active.put(d.request.id, d);
        app.queries.execute(() -> {
            List<Download> entries = new ArrayList<>();
            try (DownloadCursor cursor = app.downloads().getDownloadIndex().getDownloads()) {
                while (cursor.moveToNext()) {
                    Download saved = cursor.getDownload();
                    entries.add(active.containsKey(saved.request.id) ? active.get(saved.request.id) : saved);
                }
                handler.post(() -> { querying = false; if (!isDestroyed() && visible && page == 1) showDownloads(entries); });
            } catch (IOException e) {
                handler.post(() -> { querying = false; if (!isDestroyed()) message("无法读取下载记录"); });
            }
        });
    }
    private void updateProgress(Download d) {
        TextView label = rowStatus.get(d.request.id);
        if (label != null) label.setText(stateText(d) + " · " + String.format(Locale.CHINA, "%.1f MB", d.getBytesDownloaded() / 1048576.0)
                + (app.problems().skipped(d.request.id).isEmpty() ? "" : " · 已丢弃 " + app.problems().skipped(d.request.id).size() + " 片（视频缺段）"));
        ProgressBar progress = rowProgress.get(d.request.id);
        if (progress != null) {
            progress.setProgress(d.state == Download.STATE_COMPLETED ? 100 : Math.max(0, (int) d.getPercentDownloaded()));
            progress.setIndeterminate(d.state == Download.STATE_DOWNLOADING && d.getPercentDownloaded() < 0);
        }
    }
    private void showDownloads(List<Download> entries) {
        StringBuilder signature = new StringBuilder();
        for (Download d : entries) signature.append(d.request.id).append(':').append(d.state).append(';');
        if (signature.toString().equals(lastRows) && !rowStatus.isEmpty()) {
            for (Download d : entries) updateProgress(d);
            return;
        }
        lastRows = signature.toString();
        rowStatus.clear(); rowProgress.clear();
        list.removeAllViews();
        if (entries.isEmpty()) { list.addView(empty); return; }
        for (Download d : entries) {
            LinearLayout card = card();
            add(list, card, 8);
            String name = new String(d.request.data, StandardCharsets.UTF_8);
            card.addView(text(name.isEmpty() ? "视频" : name, 17, ink));
            TextView detail = text("", 13, green);
            rowStatus.put(d.request.id, detail);
            add(card, detail, 6);
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            rowProgress.put(d.request.id, progress);
            progress.setProgress(d.state == Download.STATE_COMPLETED ? 100 : Math.max(0, (int) d.getPercentDownloaded()));
            progress.setIndeterminate(d.state == Download.STATE_DOWNLOADING && d.getPercentDownloaded() < 0);
            add(card, progress, 8);
            updateProgress(d);
            LinearLayout actions = row();
            if (d.state == Download.STATE_COMPLETED) {
                actions.addView(button("离线播放", true, v -> startActivity(PlayerActivity.offline(this, d.request))), weighted());
            } else if (d.state == Download.STATE_FAILED) {
                actions.addView(button("处理失败", false, v -> handleFailures(d)), weighted());
            } else if (d.state != Download.STATE_REMOVING) {
                boolean stopped = d.stopReason != Download.STOP_REASON_NONE;
                actions.addView(button(stopped ? "继续" : "暂停", false, v ->
                        DownloadService.sendSetStopReason(this, VideoDownloadService.class, d.request.id, stopped ? 0 : 1, false)), weighted());
            }
            if ((d.state == Download.STATE_COMPLETED || d.state == Download.STATE_FAILED) && !app.problems().skipped(d.request.id).isEmpty()) {
                actions.addView(button("补全", false, v -> {
                    app.problems().restore(d.request.id);
                    DownloadService.sendAddDownload(this, VideoDownloadService.class, d.request, false);
                }), weighted());
            }
            actions.addView(button("删除", false, v -> new AlertDialog.Builder(this)
                    .setTitle("删除下载？").setMessage("将移除此视频和本地下载文件。")
                    .setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> {
                        DownloadService.sendRemoveDownload(this, VideoDownloadService.class, d.request.id, false);
                    }).show()), weighted());
            add(card, actions, 8);
        }
    }
    private void handleFailures(Download d) {
        List<DownloadProblems.Failure> failures = app.problems().failures(d.request.id);
        String[] labels = new String[failures.size()];
        boolean[] selected = new boolean[failures.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = failures.get(i).label + (failures.get(i).skippable ? "" : "（不可丢弃）");
        new AlertDialog.Builder(this).setTitle("失败资源 " + failures.size() + " 个")
                .setMultiChoiceItems(labels, selected, (dialog, which, checked) -> selected[which] = checked)
                .setNegativeButton("取消", null)
                .setNeutralButton("重试失败资源", (dialog, which) -> DownloadService.sendAddDownload(this, VideoDownloadService.class, d.request, false))
                .setPositiveButton("丢弃勾选分片", (dialog, which) -> {
                    Set<String> ids = new HashSet<>();
                    for (int i = 0; i < selected.length; i++) if (selected[i] && failures.get(i).skippable) ids.add(failures.get(i).id);
                    if (ids.isEmpty()) { message("请勾选可丢弃的视频分片；密钥和播放列表不可丢弃"); return; }
                    new AlertDialog.Builder(this).setTitle("丢弃 " + ids.size() + " 个分片？")
                            .setMessage("视频会缺少这些片段，播放可能出现跳跃或短暂无画面。已下载内容保留。")
                            .setNegativeButton("取消", null).setPositiveButton("丢弃并继续", (confirm, action) -> {
                                app.problems().discard(d.request.id, ids);
                                DownloadService.sendAddDownload(this, VideoDownloadService.class, d.request, false);
                            }).show();
                }).show();
    }
    private String stateText(Download d) {
        switch (d.state) {
            case Download.STATE_COMPLETED: return "已完成";
            case Download.STATE_DOWNLOADING: return d.getPercentDownloaded() < 0 ? "下载中" : String.format(Locale.CHINA, "下载中 %.0f%%", d.getPercentDownloaded());
            case Download.STATE_STOPPED: return "已暂停";
            case Download.STATE_FAILED: return "部分资源失败 · 可重试或丢弃分片";
            case Download.STATE_REMOVING: return "删除中";
            default: return app.downloads().getNotMetRequirements() != 0 ? "等待网络" : "排队中";
        }
    }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout card() {
        LinearLayout v = column(); v.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(18)); v.setBackground(bg); return v;
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private Button button(String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this); b.setText(label); b.setTextSize(14); b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : green);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? green : Color.rgb(230, 242, 237)));
        b.setOnClickListener(click); return b;
    }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, dp(50), 1); }
    private void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(margin); parent.addView(child, p);
    }
    @Override protected void onStart() { super.onStart(); visible = true; showPage(page); }
    @Override protected void onStop() { visible = false; handler.removeCallbacks(refresh); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", page);
        state.putString("url", url.getText().toString()); state.putString("title", title.getText().toString()); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (preparing != null) preparing.release();
        super.onDestroy();
    }
}
