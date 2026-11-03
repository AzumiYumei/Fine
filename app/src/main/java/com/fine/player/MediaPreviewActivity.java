package com.fine.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

@androidx.media3.common.util.UnstableApi
public final class MediaPreviewActivity extends Activity {
    private ExoPlayer player;
    private PlayerView video;
    private TextView status;
    private String url, type;
    private long position;
    private boolean play = true;
    private boolean frameReady;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> {
        if (player != null && player.getPlaybackState() == Player.STATE_BUFFERING) {
            player.stop(); status.setText("预览超时，可返回网页播放。登录或防盗链资源可能无法独立预览。");
        }
    };
    static Intent intent(Context context, BrowserResources.Entry entry) {
        return new Intent(context, MediaPreviewActivity.class).putExtra("url", entry.url)
                .putExtra("type", entry.type).putExtra("title", MediaPreviews.name(entry));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { url = Links.normalize(getIntent().getStringExtra("url")); }
        catch (Exception e) { finish(); return; }
        type = getIntent().getStringExtra("type");
        if (state != null) { position = state.getLong("position"); play = state.getBoolean("playing", true); }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        Button back = new Button(this); back.setText("返回资源列表"); back.setOnClickListener(v -> finish());
        QuietStyle.button(back, QuietStyle.PAPER, QuietStyle.INK); root.addView(back);
        TextView title = new TextView(this); title.setText(getIntent().getStringExtra("title")); title.setTextSize(18); title.setTextColor(QuietStyle.INK);
        title.setPadding(24, 20, 24, 20); root.addView(title);
        video = (PlayerView)getLayoutInflater().inflate(R.layout.media_preview, root, false);
        root.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this); status.setPadding(24, 24, 24, 24); status.setText("正在读取预览…"); root.addView(status);
        TextView source = new TextView(this); source.setText(MediaPreviews.host(url) + " · 预览会使用网络流量"); source.setTextColor(QuietStyle.MUTED);
        source.setPadding(24, 0, 24, 24); root.addView(source);
        setContentView(root);
    }
    @Override protected void onStart() {
        super.onStart(); if (url == null || isFinishing()) return;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory().setConnectTimeoutMs(10000).setReadTimeoutMs(10000);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)))
                .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(3000, 10000, 500, 1000).build()).build();
        video.setPlayer(player);
        frameReady = false;
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                handler.removeCallbacks(timeout);
                if (state == Player.STATE_BUFFERING) handler.postDelayed(timeout, 15000);
                if (state == Player.STATE_READY) updateDetails();
            }
            @Override public void onTimelineChanged(Timeline timeline, int reason) { updateDetails(); }
            @Override public void onRenderedFirstFrame() { frameReady = true; handler.postDelayed(() -> capture(), 150); }
            @Override public void onVideoSizeChanged(VideoSize size) {
                MediaPreviews.Info info = MediaPreviews.info(url); info.width = size.width; info.height = size.height;
            }
            @Override public void onPlayerError(PlaybackException error) {
                handler.removeCallbacks(timeout);
                status.setText("无法预览：链接可能已过期、需要登录或格式不受支持。可以返回网页继续播放。");
            }
        });
        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        if ("HLS".equals(type)) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(item.build()); player.seekTo(position); player.prepare(); player.setPlayWhenReady(play);
    }
    private void updateDetails() {
        if (player == null) return;
        MediaPreviews.Info info = MediaPreviews.info(url);
        info.live = player.isCurrentMediaItemLive();
        if (player.getDuration() != C.TIME_UNSET && player.getDuration() >= 0) info.duration = player.getDuration();
        status.setText(MediaPreviews.duration(info) + (info.width > 0 ? " · " + info.width + " × " + info.height : ""));
    }
    private void capture() {
        if (!frameReady) return;
        if (video.getVideoSurfaceView() instanceof TextureView) {
            TextureView texture = (TextureView)video.getVideoSurfaceView();
            if (texture.isAvailable() && texture.getWidth() > 0) {
                try { MediaPreviews.image(url, texture.getBitmap(320, Math.max(1, Math.min(480, 320 * texture.getHeight() / texture.getWidth())))); }
                catch (RuntimeException ignored) { }
            }
        }
    }
    @Override protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) { position = player.getCurrentPosition(); play = player.getPlayWhenReady(); capture(); video.setPlayer(null); player.release(); player = null; }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("position", player == null ? position : player.getCurrentPosition());
        state.putBoolean("playing", player == null ? play : player.getPlayWhenReady()); super.onSaveInstanceState(state);
    }
}
