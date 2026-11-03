package com.fine.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

final class ResourceCards extends BaseAdapter {
    interface Preview { void open(BrowserResources.Entry entry); }
    private final Context context;
    private final Preview preview;
    private List<BrowserResources.Entry> entries = new ArrayList<>();
    ResourceCards(Context context, Preview preview) { this.context = context; this.preview = preview; }
    void update(List<BrowserResources.Entry> entries) { this.entries = new ArrayList<>(entries); notifyDataSetChanged(); }
    @Override public int getCount() { return entries.size(); }
    @Override public BrowserResources.Entry getItem(int position) { return entries.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public View getView(int position, View recycled, ViewGroup parent) {
        LinearLayout row;
        Holder holder;
        if (recycled instanceof LinearLayout) { row = (LinearLayout)recycled; holder = (Holder)row.getTag(); }
        else {
            row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(10), 0, dp(10));
            holder = new Holder();
            FrameLayout thumb = new FrameLayout(context); thumb.setBackground(QuietStyle.panel(context, QuietStyle.SOFT, 0));
            holder.image = new ImageView(context); holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.addView(holder.image, new FrameLayout.LayoutParams(-1, -1));
            holder.prompt = text(12, QuietStyle.ACCENT); holder.prompt.setGravity(Gravity.CENTER);
            thumb.addView(holder.prompt, new FrameLayout.LayoutParams(-1, -1));
            holder.thumb = thumb;
            row.addView(thumb, new LinearLayout.LayoutParams(dp(108), dp(72)));
            LinearLayout details = new LinearLayout(context); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(12), 0, 0, 0);
            holder.title = text(15, QuietStyle.INK); holder.title.setMaxLines(2); holder.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            holder.meta = text(12, QuietStyle.ACCENT); holder.source = text(12, QuietStyle.MUTED); holder.source.setSingleLine(); holder.source.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            details.addView(holder.title); details.addView(holder.meta); details.addView(holder.source);
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1)); row.setTag(holder);
        }
        BrowserResources.Entry entry = getItem(position);
        MediaPreviews.Info info = MediaPreviews.info(entry.url);
        Bitmap cover = MediaPreviews.image(entry.url);
        holder.image.setImageBitmap(cover);
        boolean playable = MediaPreviews.canPreview(entry);
        holder.prompt.setText(cover != null ? "▶" : playable ? "▶\n点按预览" : entry.type);
        holder.prompt.setTextColor(cover != null ? Color.WHITE : QuietStyle.ACCENT);
        holder.prompt.setBackgroundColor(cover != null ? 0x33000000 : Color.TRANSPARENT);
        holder.thumb.setOnClickListener(playable ? v -> preview.open(entry) : null);
        holder.thumb.setContentDescription(playable ? "预览 " + MediaPreviews.name(entry) : entry.type);
        holder.title.setText(MediaPreviews.name(entry));
        holder.meta.setText(entry.type + " · " + (entry.standalone() ? MediaPreviews.duration(info) : "资源文件")
                + (info.width > 0 ? " · " + info.width + "×" + info.height : ""));
        holder.source.setText(MediaPreviews.host(entry.url));
        return row;
    }
    private TextView text(int size, int color) { TextView text = new TextView(context); text.setTextSize(size); text.setTextColor(color); return text; }
    private int dp(int n) { return QuietStyle.dp(context, n); }
    private static final class Holder { FrameLayout thumb; ImageView image; TextView prompt, title, meta, source; }
}
