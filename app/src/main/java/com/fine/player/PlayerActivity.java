package com.fine.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.nio.charset.StandardCharsets;

@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private PlayerView video;
    private LinearLayout header;
    private TextView status;
    private Button videoFullscreen;
    private ExoPlayer player;
    private MediaItem media;
    private DownloadRequest download;
    private boolean fullscreen, playWhenReady = true;
    private long position;

    static Intent online(Context context, String url, String title) {
        return new Intent(context, PlayerActivity.class).putExtra("url", url).putExtra("title", title);
    }
    static Intent offline(Context context, DownloadRequest request) {
        return new Intent(context, PlayerActivity.class).putExtra("download", request)
                .putExtra("title", new String(request.data, StandardCharsets.UTF_8));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        download = getIntent().getParcelableExtra("download");
        try {
            media = download != null ? download.toMediaItem() : new MediaItem.Builder()
                    .setUri(Links.normalize(getIntent().getStringExtra("url") == null ? "" : getIntent().getStringExtra("url")))
                    .setMimeType(MimeTypes.APPLICATION_M3U8).build();
        } catch (IllegalArgumentException e) { finish(); return; }
        if (state != null) {
            position = state.getLong("position", 0);
            playWhenReady = state.getBoolean("playing", true);
            fullscreen = state.getBoolean("fullscreen", false);
        } else fullscreen = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        header = new LinearLayout(this);
        header.setId(R.id.player_header);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("返回"); back.setContentDescription("返回上一页");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(76), dp(56)));
        TextView title = new TextView(this);
        String name = getIntent().getStringExtra("title");
        title.setText(name == null || name.isEmpty() ? "视频播放" : name);
        title.setTextColor(Color.WHITE); title.setTextSize(18); title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button expand = new Button(this);
        expand.setId(R.id.fullscreen_toggle); expand.setText("全屏");
        expand.setOnClickListener(v -> setFullscreen(true, true));
        header.addView(expand, new LinearLayout.LayoutParams(dp(76), dp(56)));
        root.addView(header);
        video = new PlayerView(this);
        video.setId(R.id.player_view);
        video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        videoFullscreen = new Button(this);
        videoFullscreen.setText("全屏");
        videoFullscreen.setOnClickListener(v -> setFullscreen(!fullscreen, true));
        FrameLayout.LayoutParams toggle = new FrameLayout.LayoutParams(dp(100), dp(48), android.view.Gravity.TOP | android.view.Gravity.RIGHT);
        toggle.setMargins(dp(12), dp(12), dp(12), dp(12));
        video.addView(videoFullscreen, toggle);
        video.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> videoFullscreen.setVisibility(fullscreen ? visibility : View.GONE));
        root.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this);
        status.setTextColor(Color.LTGRAY); status.setPadding(dp(20), dp(12), dp(20), dp(16));
        status.setText(download != null ? "离线播放" : "在线播放");
        root.addView(status);
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        setFullscreen(fullscreen, false);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void setFullscreen(boolean value, boolean rotate) {
        fullscreen = value;
        header.setVisibility(value ? View.GONE : View.VISIBLE);
        status.setVisibility(value ? View.GONE : View.VISIBLE);
        videoFullscreen.setText(value ? "退出全屏" : "全屏");
        videoFullscreen.setVisibility(value ? View.VISIBLE : View.GONE);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController bars = getWindow().getInsetsController();
            if (bars != null) {
                bars.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (value) bars.hide(WindowInsets.Type.systemBars()); else bars.show(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(value ? View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE : 0);
        }
        if (rotate) setRequestedOrientation(value ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        setFullscreen(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE, false);
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus && video != null) setFullscreen(fullscreen, false);
    }
    @Override public void onBackPressed() {
        if (fullscreen) setFullscreen(false, true); else super.onBackPressed();
    }
    private void startPlayer() {
        if (media == null || player != null) return;
        FineApp app = (FineApp) getApplication();
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(app.source(download != null))).build();
        video.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean playing) {
                if (playing) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) status.setText("正在加载视频…");
                else if (state == Player.STATE_READY) status.setText(download != null ? "离线播放 · 无需网络" : "在线播放");
                else if (state == Player.STATE_ENDED) status.setText("播放完毕");
            }
            @Override public void onPlayerError(PlaybackException error) {
                String message = getString(R.string.playback_error, error.getErrorCodeName());
                status.setText(message);
                // Remains visible even with the surrounding page hidden in fullscreen.
                video.setCustomErrorMessage(message);
                video.showController();
            }
        });
        if (download != null) player.setMediaSource(new HlsMediaSource.Factory(app.source(true))
                .setPlaylistParserFactory(GapPlaylistParser.factory(app.problems().skipped(download.id)))
                .createMediaSource(media), position);
        else player.setMediaItem(media, position);
        player.prepare(); player.setPlayWhenReady(playWhenReady);
    }
    private void releasePlayer() {
        if (player != null) {
            position = player.getCurrentPosition(); playWhenReady = player.getPlayWhenReady();
            video.setPlayer(null); player.release(); player = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onStart() { super.onStart(); if (!isFinishing()) startPlayer(); }
    @Override protected void onStop() { releasePlayer(); super.onStop(); }
    @Override protected void onDestroy() { releasePlayer(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("position", player != null ? player.getCurrentPosition() : position);
        state.putBoolean("playing", player != null ? player.getPlayWhenReady() : playWhenReady);
        state.putBoolean("fullscreen", fullscreen);
        super.onSaveInstanceState(state);
    }
}
