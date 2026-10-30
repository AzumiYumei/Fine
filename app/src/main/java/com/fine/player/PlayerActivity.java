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
    private LinearLayout controls;
    private androidx.media3.ui.DefaultTimeBar progress;
    private ImageButton videoFullscreen;
    private boolean scrubbing;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hide = () -> { if (!scrubbing && controls != null) controls.setVisibility(View.GONE); };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player != null && !scrubbing) {
                long duration = player.getDuration();
                progress.setDuration(duration == C.TIME_UNSET ? 0 : duration);
                progress.setPosition(player.getCurrentPosition());
                progress.setBufferedPosition(player.getBufferedPosition());
                progress.setEnabled(player.isCurrentMediaItemSeekable());
            }
            handler.postDelayed(this, 250);
        }
    };
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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        video = new PlayerView(this);
        video.setId(R.id.player_view);
        video.setUseController(false);
        video.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        video.setOnClickListener(v -> {
            if (controls.getVisibility() == View.VISIBLE) { controls.setVisibility(View.GONE); handler.removeCallbacks(hide); }
            else showControls();
        });
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        controls = new LinearLayout(this);
        controls.setId(R.id.player_controls);
        controls.setGravity(android.view.Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(12), 0, dp(8), 0);
        controls.setBackgroundColor(Color.TRANSPARENT);
        progress = new androidx.media3.ui.DefaultTimeBar(this, null);
        progress.setId(R.id.playback_progress);
        progress.setPlayedColor(Color.WHITE);
        progress.setScrubberColor(Color.WHITE);
        progress.setBufferedColor(0x99FFFFFF);
        progress.setUnplayedColor(0x55777777);
        progress.addListener(new androidx.media3.ui.TimeBar.OnScrubListener() {
            @Override public void onScrubStart(androidx.media3.ui.TimeBar bar, long value) { scrubbing = true; handler.removeCallbacks(hide); }
            @Override public void onScrubMove(androidx.media3.ui.TimeBar bar, long value) { }
            @Override public void onScrubStop(androidx.media3.ui.TimeBar bar, long value, boolean canceled) {
                if (player != null && !canceled) player.seekTo(value);
                scrubbing = false; showControls();
            }
        });
        controls.addView(progress, new LinearLayout.LayoutParams(0, dp(44), 1));
        ImageButton settings = icon(R.drawable.ic_settings, "播放设置");
        settings.setId(R.id.playback_settings);
        settings.setOnClickListener(v -> showSettings());
        controls.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));
        videoFullscreen = icon(R.drawable.ic_fullscreen, "全屏");
        videoFullscreen.setId(R.id.fullscreen_toggle);
        videoFullscreen.setOnClickListener(v -> setFullscreen(!fullscreen, true));
        controls.addView(videoFullscreen, new LinearLayout.LayoutParams(dp(44), dp(44)));
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(-1, dp(48), android.view.Gravity.BOTTOM);
        bottom.bottomMargin = dp(8);
        root.addView(controls, bottom);
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        setFullscreen(fullscreen, false);
    }
    private ImageButton icon(int resource, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(resource); button.setContentDescription(description);
        button.setBackgroundColor(Color.TRANSPARENT); button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return button;
    }
    private void showControls() { controls.setVisibility(View.VISIBLE); handler.removeCallbacks(hide); handler.postDelayed(hide, 3500); }
    private void showSettings() {
        handler.removeCallbacks(hide);
        if (player == null) return;
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle("播放设置")
                .setItems(new String[]{player.isPlaying() ? "暂停" : "继续播放", "播放速度", "画面比例", "重新加载"}, (d, which) -> {
                    if (player == null) return;
                    if (which == 0) player.setPlayWhenReady(!player.getPlayWhenReady());
                    if (which == 1) {
                        float[] speeds = {.5f, 1f, 1.25f, 1.5f, 2f};
                        android.app.AlertDialog speed = new android.app.AlertDialog.Builder(this).setTitle("播放速度")
                                .setItems(new String[]{"0.5×", "1×", "1.25×", "1.5×", "2×"}, (s, index) -> { if (player != null) player.setPlaybackSpeed(speeds[index]); }).create();
                        showUndimmed(speed);
                    }
                    if (which == 2) {
                        android.app.AlertDialog aspect = new android.app.AlertDialog.Builder(this).setTitle("画面比例")
                                .setItems(new String[]{"适应画面", "填满（裁剪）"}, (s, index) -> video.setResizeMode(index == 0 ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM)).create();
                        showUndimmed(aspect);
                    }
                    if (which == 3) { player.prepare(); player.play(); }
                }).create();
        dialog.setOnDismissListener(d -> showControls());
        showUndimmed(dialog);
    }
    private void showUndimmed(android.app.AlertDialog dialog) {
        dialog.show();
        if (dialog.getWindow() != null) { dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); dialog.getWindow().setDimAmount(0); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void setFullscreen(boolean value, boolean rotate) {
        fullscreen = value;
        videoFullscreen.setImageResource(value ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
        videoFullscreen.setContentDescription(value ? "退出全屏" : "全屏");
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
            @Override public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this, getString(R.string.playback_error, error.getErrorCodeName()), Toast.LENGTH_LONG).show();
                showControls();
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
    @Override protected void onStart() { super.onStart(); if (!isFinishing()) { startPlayer(); handler.post(tick); showControls(); } }
    @Override protected void onStop() { handler.removeCallbacksAndMessages(null); releasePlayer(); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); releasePlayer(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("position", player != null ? player.getCurrentPosition() : position);
        state.putBoolean("playing", player != null ? player.getPlayWhenReady() : playWhenReady);
        state.putBoolean("fullscreen", fullscreen);
        super.onSaveInstanceState(state);
    }
}
