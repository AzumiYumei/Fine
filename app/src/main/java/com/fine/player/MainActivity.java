package com.fine.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private final int ink = QuietStyle.INK, green = QuietStyle.ACCENT;
    private FineApp app;
    private EditText url, title;
    private TextView empty;
    private LinearLayout list, pendingList;
    private Button downloadButton;
    private DownloadHelper preparing;
    private ScrollView downloadPage, libraryPage, settingsPage;
    private TextView locationLabel;
    private List<Download> latestDownloads = Collections.emptyList();
    private Button downloadTab, libraryTab, settingsTab;
    private int page;
    private boolean visible;
    private String lastRows = "#";
    private boolean querying;
    private final Map<String, TextView> rowStatus = new HashMap<>();
    private final Map<String, ProgressBar> rowProgress = new HashMap<>();
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (visible) { renderDownloads(); handler.postDelayed(this, 1000); } }
    };

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
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
        shell.setBackgroundColor(QuietStyle.PAPER);
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
        settingsPage = new ScrollView(this);
        settingsPage.setId(R.id.settings_page);
        pages.addView(settingsPage, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout tabs = row();
        tabs.setPadding(dp(16), dp(12), dp(16), dp(12));
        tabs.setBackgroundColor(Color.WHITE);
        downloadTab = button("下载", true, v -> showPage(0));
        downloadTab.setId(R.id.download_tab);
        libraryTab = button("已下载", false, v -> showPage(1));
        libraryTab.setId(R.id.library_tab);
        tabs.addView(downloadTab, weighted());
        tabs.addView(libraryTab, weighted());
        settingsTab = button("设置", false, v -> showPage(2));
        settingsTab.setId(R.id.settings_tab);
        tabs.addView(settingsTab, weighted());
        shell.addView(tabs);
        setContentView(shell);
        LinearLayout root = column();
        root.setPadding(dp(24), dp(20), dp(24), dp(20));
        downloadPage.addView(root);
        root.addView(text("下载", 22, ink));

        LinearLayout input = card();
        add(root, input, 24);
        input.addView(text("链接 / URL", 12, ink));
        url = new EditText(this);
        url.setId(R.id.video_url);
        url.setHint("https://example.com/video.m3u8");
        url.setTextSize(14);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setSingleLine(true);
        QuietStyle.input(url);
        LinearLayout.LayoutParams urlSize = new LinearLayout.LayoutParams(-1, dp(52));
        urlSize.topMargin = dp(10);
        input.addView(url, urlSize);
        title = new EditText(this);
        title.setId(R.id.video_title);
        title.setHint("视频名称（可选）");
        title.setTextSize(14);
        title.setSingleLine(true);
        QuietStyle.input(title);
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(-1, dp(48));
        titleSize.topMargin = dp(10);
        input.addView(title, titleSize);
        LinearLayout actions = row();
        actions.addView(button("粘贴", false, v -> paste()), weighted());
        Button play = button("播放", true, v -> playInput());
        play.setId(R.id.play_online);
        QuietStyle.button(play, QuietStyle.ACCENT, Color.WHITE);
        actions.addView(play, weighted());
        downloadButton = button("下载", false, v -> download());
        QuietStyle.button(downloadButton, QuietStyle.ACCENT, Color.WHITE);
        actions.addView(downloadButton, weighted());
        add(input, actions, 12);
        add(input, text("不限速 · 自动重试 3 次 · 使用当前网络", 12, QuietStyle.MUTED), 10);

        pendingList = column();
        add(root, pendingList, 20);
        LinearLayout library = column();
        library.setPadding(dp(24), dp(20), dp(24), dp(20));
        libraryPage.addView(library);
        library.addView(text("已下载", 22, ink));
        list = column();
        add(library, list, 20);
        empty = text("暂无已下载视频", 14, QuietStyle.MUTED);
        empty.setPadding(dp(20), dp(32), dp(20), dp(32));
        empty.setBackground(QuietStyle.panel(this, QuietStyle.SOFT, 1));
        empty.setTextColor(ink);
        list.addView(empty);
        LinearLayout settings = column();
        settings.setPadding(dp(24), dp(20), dp(24), dp(20));
        settingsPage.addView(settings);
        settings.addView(text("设置", 22, ink));
        LinearLayout storage = card();
        add(settings, storage, 24);
        storage.addView(text("下载位置", 16, ink));
        locationLabel = text(app.library().destinationLabel(), 13, QuietStyle.MUTED);
        add(storage, locationLabel, 10);
        add(storage, button("选择文件夹", true, v -> chooseDirectory()), 12);
        add(storage, button("使用应用内部存储", false, v -> {
            try { app.library().destination(""); locationLabel.setText(app.library().destinationLabel()); }
            catch (IOException e) { message(e.getMessage()); }
        }), 8);
        add(storage, text("新下载完成后保存到此目录；旧视频保持原位。下载和转换期间需要内部临时空间。", 12, QuietStyle.MUTED), 12);
        LinearLayout conversion = card();
        add(settings, conversion, 16);
        conversion.addView(text("M3U8 转 MP4", 16, ink));
        add(conversion, text("将已下载视频转为 MP4，保持画质。可选择转换成功后删除源文件。", 13, QuietStyle.MUTED), 10);
        add(conversion, button("选择视频转换", true, v -> chooseConversion()), 12);
        Button concurrency = button("下载并发：" + app.parallelism() + " 线程", false, null);
        concurrency.setOnClickListener(v -> {
            int[] values = {2, 4, 6, 8};
            new AlertDialog.Builder(this).setTitle("分片并发（下次开始或继续时生效）")
                    .setItems(new String[]{"2 线程 · 较少占用", "4 线程", "6 线程 · 默认", "8 线程"}, (dialog, which) -> {
                        app.parallelism(values[which]); concurrency.setText("下载并发：" + values[which] + " 线程");
                    }).show();
        });
        add(settings, concurrency, 16);

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
        page = selected >= 0 && selected <= 2 ? selected : 0;
        downloadPage.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        libraryPage.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == 2 ? View.VISIBLE : View.GONE);
        locationLabel.setText(app.library().destinationLabel());
        downloadTab.setSelected(page == 0);
        libraryTab.setSelected(page == 1);
        settingsTab.setSelected(page == 2);
        QuietStyle.button(downloadTab, page == 0 ? QuietStyle.SOFT : Color.WHITE, page == 0 ? QuietStyle.ACCENT : QuietStyle.MUTED);
        QuietStyle.button(libraryTab, page == 1 ? QuietStyle.SOFT : Color.WHITE, page == 1 ? QuietStyle.ACCENT : QuietStyle.MUTED);
        QuietStyle.button(settingsTab, page == 2 ? QuietStyle.SOFT : Color.WHITE, page == 2 ? QuietStyle.ACCENT : QuietStyle.MUTED);
        handler.removeCallbacks(refresh);
        if (visible) handler.post(refresh);
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(url.getWindowToken(), 0);
    }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent); setIntent(intent); showPage(intent.getIntExtra("page", page));
    }
    @Override public void onBackPressed() {
        if (page != 0) showPage(0); else super.onBackPressed();
    }

    private void download() {
        final String link;
        try { link = inputLink(); }
        catch (IllegalArgumentException e) { url.setError(e.getMessage()); return; }
        if (preparing != null) return;
        try {
            Download existing = app.downloads().getDownloadIndex().getDownload(Links.id(link));
            if (existing != null) { message("此链接已在下载列表中"); showPage(existing.state == Download.STATE_COMPLETED ? 1 : 0); return; }
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
                    app.library().put(request.id, "destination", app.library().destination());
                    DownloadService.sendAddDownload(MainActivity.this, VideoDownloadService.class, request, false);
                    message("已加入下载队列");
                } catch (IOException e) { message(e.getMessage()); } finally { finishPreparation(helper); }
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
                handler.post(() -> { querying = false; if (!isDestroyed() && visible) showDownloads(entries); });
            } catch (IOException e) {
                handler.post(() -> { querying = false; if (!isDestroyed()) message("无法读取下载记录"); });
            }
        });
    }
    private void updateProgress(Download d) {
        TextView label = rowStatus.get(d.request.id);
        if (d.state == Download.STATE_COMPLETED) { if (label != null) label.setText(savedStatus(d.request.id)); return; }
        if (label != null) label.setText(stateText(d) + " · " + String.format(Locale.CHINA, "%.1f MB", d.getBytesDownloaded() / 1048576.0)
                + (app.problems().skipped(d.request.id).isEmpty() ? "" : " · 已丢弃 " + app.problems().skipped(d.request.id).size() + " 片（视频缺段）"));
        ProgressBar progress = rowProgress.get(d.request.id);
        if (progress != null) {
            progress.setProgress(d.state == Download.STATE_COMPLETED ? 100 : Math.max(0, (int) d.getPercentDownloaded()));
            progress.setIndeterminate(d.state == Download.STATE_DOWNLOADING && d.getPercentDownloaded() < 0);
        }
    }
    private void showDownloads(List<Download> entries) {
        latestDownloads = entries;
        StringBuilder signature = new StringBuilder();
        for (Download d : entries) signature.append(d.request.id).append(':').append(d.state)
                .append(app.library().get(d.request.id, "mp4")).append(app.library().get(d.request.id, "hls")).append(';');
        if (signature.toString().equals(lastRows)) {
            for (Download d : entries) updateProgress(d);
            return;
        }
        lastRows = signature.toString();
        rowStatus.clear(); rowProgress.clear();
        list.removeAllViews();
        pendingList.removeAllViews();
        if (entries.isEmpty()) { list.addView(empty); return; }
        boolean hasCompleted = false;
        for (Download d : entries) {
            if (d.state == Download.STATE_COMPLETED) {
                hasCompleted = true;
                showSavedVideo(d);
                continue;
            }
            LinearLayout card = card();
            add(pendingList, card, 12);
            String name = new String(d.request.data, StandardCharsets.UTF_8);
            TextView nameView = text(name.isEmpty() ? "视频" : name, 20, ink);
            nameView.setTypeface(null, Typeface.BOLD);
            add(card, nameView, 12);
            TextView detail = text("", 13, green);
            rowStatus.put(d.request.id, detail);
            add(card, detail, 6);
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(QuietStyle.ACCENT));
            progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(QuietStyle.PAPER));
            rowProgress.put(d.request.id, progress);
            progress.setProgress(d.state == Download.STATE_COMPLETED ? 100 : Math.max(0, (int) d.getPercentDownloaded()));
            progress.setIndeterminate(d.state == Download.STATE_DOWNLOADING && d.getPercentDownloaded() < 0);
            add(card, progress, 8);
            updateProgress(d);
            LinearLayout actions = row();
            if (d.state == Download.STATE_FAILED) {
                actions.addView(button("处理失败", false, v -> handleFailures(d)), weighted());
            } else if (d.state != Download.STATE_REMOVING) {
                boolean stopped = d.stopReason != Download.STOP_REASON_NONE;
                actions.addView(button(stopped ? "继续" : "暂停", false, v ->
                        DownloadService.sendSetStopReason(this, VideoDownloadService.class, d.request.id, stopped ? 0 : 1, false)), weighted());
            }
            actions.addView(button("删除", false, v -> new AlertDialog.Builder(this)
                    .setTitle("删除下载？").setMessage("将移除此视频和本地下载文件。")
                    .setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> {
                        app.thumbnails().remove(d.request.id);
                        DownloadService.sendRemoveDownload(this, VideoDownloadService.class, d.request.id, false);
                    }).show()), weighted());
            add(card, actions, 8);
        }
        if (!hasCompleted) list.addView(empty);
    }
    private void showSavedVideo(Download d) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        add(list, card, 8);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cover.setBackgroundColor(QuietStyle.SOFT);
        cover.setImageResource(R.drawable.ic_video_placeholder);
        cover.setContentDescription("播放视频");
        card.addView(cover, new LinearLayout.LayoutParams(dp(104), dp(64)));
        app.thumbnails().load(d.request, cover);
        String name = new String(d.request.data, StandardCharsets.UTF_8);
        LinearLayout details = column();
        LinearLayout.LayoutParams detailsSize = new LinearLayout.LayoutParams(0, -2, 1);
        detailsSize.leftMargin = dp(12);
        card.addView(details, detailsSize);
        TextView nameView = text(name.isEmpty() ? "视频" : name, 15, ink);
        nameView.setMaxLines(2);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(nameView);
        TextView detail = text(savedStatus(d.request.id), 12, QuietStyle.MUTED);
        rowStatus.put(d.request.id, detail);
        add(details, detail, 5);
        View.OnClickListener play = v -> startActivity(PlayerActivity.saved(this, d.request, app.library()));
        card.setOnClickListener(play); cover.setOnClickListener(play);
        card.setContentDescription("播放 " + name);
        View.OnLongClickListener menu = v -> { savedMenu(d); return true; };
        card.setOnLongClickListener(menu);
        cover.setOnLongClickListener(menu);
    }
    private String savedStatus(String id) {
        String job = FileTaskService.status(id);
        if (!job.isEmpty()) return job;
        String format = app.library().get(id, "mp4").isEmpty() ? "M3U8" : "MP4";
        if (!app.library().get(id, "error").isEmpty()) return format + " · 处理失败，长按查看";
        if (!app.library().get(id, "incomplete").isEmpty()) return format + " · 操作中断，长按重试";
        return format + (app.problems().skipped(id).isEmpty() ? " · 离线视频" : " · 缺段");
    }
    private void savedMenu(Download d) {
        if (!FileTaskService.status(d.request.id).isEmpty()) { message("正在处理此视频，请稍候"); return; }
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        LibraryFiles files = app.library();
        String id = d.request.id;
        if (!files.get(id, "error").isEmpty()) {
            labels.add("查看处理失败原因"); actions.add(() -> new AlertDialog.Builder(this).setTitle("文件处理未完成")
                    .setMessage(files.get(id, "error")).setPositiveButton("知道了", null).show());
        }
        if (files.get(id, "mp4").isEmpty()) {
            labels.add("转为 MP4"); actions.add(() -> confirmConversion(d));
        } else if (!files.flag(id, "sourceDeleted")) {
            labels.add("删除保留的 M3U8 源文件"); actions.add(() -> new AlertDialog.Builder(this).setTitle("删除源文件？")
                    .setMessage("保留 MP4，删除此视频的 M3U8 和分片。")
                    .setNegativeButton("取消", null).setPositiveButton("删除源文件", (dialog, which) -> FileTaskService.start(this, d.request, "source", false)).show());
        }
        if (!files.flag(id, "sourceDeleted") && !files.get(id, "destination").isEmpty() && files.get(id, "hls").isEmpty()) {
            labels.add("重新保存到下载目录"); actions.add(() -> FileTaskService.start(this, d.request, "save", false));
        }
        labels.add("删除视频"); actions.add(() -> new AlertDialog.Builder(this).setTitle("删除此视频？")
                .setMessage("将删除此视频的 MP4、M3U8 和分片文件。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> FileTaskService.start(this, d.request, "delete", false)).show());
        new AlertDialog.Builder(this).setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run()).show();
    }
    private void chooseConversion() {
        ArrayList<Download> choices = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (Download d : latestDownloads) if (d.state == Download.STATE_COMPLETED && app.library().get(d.request.id, "mp4").isEmpty()) {
            choices.add(d); labels.add(new String(d.request.data, StandardCharsets.UTF_8));
        }
        if (choices.isEmpty()) { message("暂无可转换的 M3U8，请先下载视频"); return; }
        new AlertDialog.Builder(this).setTitle("选择视频").setItems(labels.toArray(new String[0]), (dialog, which) -> confirmConversion(choices.get(which))).show();
    }
    private void confirmConversion(Download d) {
        if (!FileTaskService.status(d.request.id).isEmpty()) { message("此视频正在处理中"); return; }
        LinearLayout content = column(); content.setPadding(dp(24), dp(12), dp(24), 0);
        content.addView(text("转换音视频，不重新编码。缺失片段无法恢复，外挂字幕不写入 MP4。转换期间需要额外空间，输出保存到设置中的目录。", 14, ink));
        CheckBox remove = new CheckBox(this); remove.setText("转换成功后删除 M3U8 源文件"); remove.setTextColor(ink);
        add(content, remove, 12);
        new AlertDialog.Builder(this).setTitle("转为 MP4").setView(content).setNegativeButton("取消", null)
                .setPositiveButton("开始转换", (dialog, which) -> { FileTaskService.start(this, d.request, "convert", remove.isChecked()); showPage(1); }).show();
    }
    private void chooseDirectory() {
        android.content.Intent picker = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try { startActivityForResult(picker, 42); }
        catch (android.content.ActivityNotFoundException e) { message("系统文件夹选择器不可用"); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 42 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) { message("未获得目录访问权限，请重新选择"); return; }
        app.queries.execute(() -> {
            try {
                android.net.Uri probe = LibraryFiles.create(this, LibraryFiles.treeRoot(tree.toString()), "application/octet-stream", ".fine-check-" + UUID.randomUUID());
                try { HlsExporter.writeChecked(this, probe, out -> out.write(1)); }
                finally { LibraryFiles.delete(this, probe); }
                app.library().destination(tree.toString());
                handler.post(() -> { if (!isDestroyed()) { locationLabel.setText(app.library().destinationLabel()); message("下载位置已更新"); } });
            } catch (Exception e) { handler.post(() -> message("目录无法读写，请选择其他文件夹")); }
        });
    }

    private void handleFailures(Download d) {
        List<DownloadProblems.Failure> failures = app.problems().failures(d.request.id);
        StringBuilder details = new StringBuilder();
        for (DownloadProblems.Failure failure : failures) details.append(failure.label).append("\n");
        if (app.problems().archiving(d.request.id)) {
            new AlertDialog.Builder(this).setTitle("本地归档未完成").setMessage(details + "\n本地资源不完整，无法生成可播放的视频。此任务不会再联网下载。")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("下载失败").setMessage(details.toString())
                .setNegativeButton("取消", null)
                .setNeutralButton("重试", (dialog, which) -> DownloadService.sendAddDownload(this, VideoDownloadService.class, d.request, false))
                .setPositiveButton("丢弃并保存", (dialog, which) -> new AlertDialog.Builder(this)
                        .setTitle("保存现有视频？").setMessage("所有失败分片将被丢弃。视频会缺段，保存后移至已下载，不再重试或补全。")
                        .setNegativeButton("取消", null).setPositiveButton("保存", (confirm, action) -> {
                            app.queries.execute(() -> {
                                try {
                                    app.problems().archive(d.request.id);
                                    handler.post(() -> {
                                        DownloadService.sendAddDownload(this, VideoDownloadService.class, d.request, false);
                                        showPage(1);
                                        message("正在整理本地视频");
                                    });
                                } catch (IllegalStateException e) { handler.post(() -> message(e.getMessage())); }
                            });
                        }).show()).show();
    }
    private String stateText(Download d) {
        switch (d.state) {
            case Download.STATE_COMPLETED: return "已完成";
            case Download.STATE_DOWNLOADING: return d.getPercentDownloaded() < 0 ? "下载中" : String.format(Locale.CHINA, "下载中 %.0f%%", d.getPercentDownloaded());
            case Download.STATE_STOPPED: return "已暂停";
            case Download.STATE_FAILED: return app.problems().archiving(d.request.id) ? "本地归档未完成" : "部分资源失败";
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
        v.setBackground(QuietStyle.panel(this, Color.WHITE, 0)); return v;
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private Button button(String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this); b.setText(label);
        QuietStyle.button(b, primary ? QuietStyle.ACCENT : QuietStyle.PAPER, primary ? Color.WHITE : ("删除".equals(label) ? QuietStyle.RED : ink));
        b.setOnClickListener(click); return b;
    }
    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(50), 1);
        size.setMargins(dp(3), 0, dp(3), 0); return size;
    }
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
