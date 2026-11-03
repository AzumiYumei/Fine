package com.fine.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public final class HistoryActivity extends Activity {
    private BrowserHistory history;
    private EditText search;
    private TextView empty;
    private List<BrowserHistory.Visit> entries = Collections.emptyList();
    private BaseAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int queryVersion;
    private boolean destroyed;
    private final Runnable searchLater = this::refresh;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); history = BrowserHistory.get(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(16), dp(12), dp(16), 0);
        LinearLayout header = new LinearLayout(this);
        header.addView(button("‹ 返回", v -> finish()), new LinearLayout.LayoutParams(dp(80), dp(48)));
        TextView title = text("历史记录", 20, QuietStyle.INK); title.setGravity(android.view.Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button clear = button("清空", v -> new AlertDialog.Builder(this).setTitle("清空全部历史记录？")
                .setMessage("只删除浏览记录，不影响网站登录或下载文件。")
                .setNegativeButton("取消", null).setPositiveButton("清空", (d, w) -> mutate(history::clear)).show());
        header.addView(clear, new LinearLayout.LayoutParams(dp(70), dp(48))); root.addView(header);
        Switch record = new Switch(this); record.setText("记录浏览历史"); record.setChecked(history.enabled());
        record.setPadding(0, dp(12), 0, dp(12));
        record.setOnCheckedChangeListener((v, enabled) -> history.enabled(enabled)); root.addView(record);
        TextView detail = text("仅存本机 · 最多 1000 个网址 · 长按删除单条\n关闭记录不删除已有历史，也不是无痕模式。", 12, QuietStyle.MUTED);
        detail.setPadding(0, 0, 0, dp(12)); root.addView(detail);
        search = new EditText(this); search.setId(R.id.history_search); search.setHint("搜索标题或网址"); search.setSingleLine(true); search.setTextSize(14); QuietStyle.input(search);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                queryVersion++; handler.removeCallbacks(searchLater); handler.postDelayed(searchLater, 180);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        empty = text("正在读取历史…", 14, QuietStyle.MUTED); empty.setPadding(0, dp(24), 0, dp(24)); root.addView(empty);
        ListView list = new ListView(this); list.setId(R.id.history_list); list.setEmptyView(empty);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return entries.size(); }
            @Override public Object getItem(int position) { return entries.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row;
                if (convertView instanceof LinearLayout) row = (LinearLayout)convertView;
                else {
                    row = new LinearLayout(HistoryActivity.this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0, dp(14), 0, dp(14));
                    row.addView(text("", 15, QuietStyle.INK)); row.addView(text("", 12, QuietStyle.MUTED)); row.addView(text("", 12, QuietStyle.MUTED));
                    for (int i=0; i<2; i++) { TextView t = (TextView)row.getChildAt(i); t.setMaxLines(i == 0 ? 2 : 1); t.setEllipsize(android.text.TextUtils.TruncateAt.END); }
                }
                BrowserHistory.Visit visit = entries.get(position);
                ((TextView)row.getChildAt(0)).setText(visit.title.isEmpty() ? MediaPreviews.host(visit.url) : visit.title);
                ((TextView)row.getChildAt(1)).setText(visit.url);
                ((TextView)row.getChildAt(2)).setText(new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(visit.time)));
                return row;
            }
        };
        list.setAdapter(adapter); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, view, position, id) -> {
            startActivity(new Intent(this, BrowserActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(entries.get(position).url))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish();
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            BrowserHistory.Visit visit = entries.get(position);
            new AlertDialog.Builder(this).setTitle("删除这条历史记录？").setMessage(visit.title.isEmpty() ? visit.url : visit.title)
                    .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> mutate(() -> history.delete(visit.url))).show(); return true;
        });
        setContentView(root); root.setFocusableInTouchMode(true); root.requestFocus(); refresh();
    }
    private void refresh() {
        int version = ++queryVersion; String query = search.getText().toString();
        BrowserHistory.WORKER.execute(() -> {
            try {
                List<BrowserHistory.Visit> found = history.search(query);
                handler.post(() -> {
                    if (destroyed || version != queryVersion) return;
                    entries = found; empty.setText(query.trim().isEmpty() ? "暂无浏览记录" : "没有匹配的记录"); adapter.notifyDataSetChanged();
                });
            } catch (RuntimeException e) { handler.post(() -> { if (!destroyed && version == queryVersion) empty.setText("读取历史失败，请稍后重试"); }); }
        });
    }
    private void mutate(Runnable action) {
        ++queryVersion;
        BrowserHistory.WORKER.execute(() -> {
            try { action.run(); handler.post(() -> { if (!destroyed) refresh(); }); }
            catch (RuntimeException e) { handler.post(() -> { if (!destroyed) Toast.makeText(this, "修改历史记录失败", Toast.LENGTH_SHORT).show(); }); }
        });
    }
    @Override protected void onDestroy() { destroyed = true; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private int dp(int n) { return QuietStyle.dp(this, n); }
    private TextView text(String value, int size, int color) { TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); return text; }
    private Button button(String label, View.OnClickListener click) { Button b = new Button(this); b.setText(label); QuietStyle.button(b, QuietStyle.PAPER, QuietStyle.INK); b.setOnClickListener(click); return b; }
}
