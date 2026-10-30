package com.fine.player;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Small persistent sidecar. No media bytes or signed URLs are stored here. */
final class DownloadProblems {
    static final class Failure {
        final String id, label;
        final boolean skippable;
        Failure(String id, String label, boolean skippable) {
            this.id = id; this.label = label; this.skippable = skippable;
        }
    }
    private final SharedPreferences prefs;
    DownloadProblems(Context context) { prefs = context.getSharedPreferences("download_problems", 0); }
    synchronized List<Failure> failures(String task) {
        List<Failure> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(task + ".fail", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                result.add(new Failure(o.getString("id"), o.getString("label"), o.getBoolean("skip")));
            }
        } catch (JSONException e) { throw new IllegalStateException("损坏的下载失败记录", e); }
        return result;
    }
    synchronized void record(String task, Failure failure) {
        List<Failure> all = failures(task);
        for (Iterator<Failure> i = all.iterator(); i.hasNext();) if (i.next().id.equals(failure.id)) i.remove();
        all.add(failure); save(task, all);
    }
    synchronized void resolved(String task, String id) {
        List<Failure> all = failures(task);
        boolean changed = false;
        for (Iterator<Failure> i = all.iterator(); i.hasNext();) if (i.next().id.equals(id)) { i.remove(); changed = true; }
        if (changed) save(task, all);
    }
    private void save(String task, List<Failure> all) {
        JSONArray array = new JSONArray();
        try {
            for (Failure f : all) array.put(new JSONObject().put("id", f.id).put("label", f.label).put("skip", f.skippable));
        } catch (JSONException e) { throw new IllegalStateException(e); }
        prefs.edit().putString(task + ".fail", array.toString()).apply();
    }
    synchronized Set<String> skipped(String task) {
        return new HashSet<>(prefs.getStringSet(task + ".skip", Collections.emptySet()));
    }
    synchronized void discard(String task, Set<String> ids) {
        Set<String> skip = skipped(task);
        for (Failure f : failures(task)) if (f.skippable && ids.contains(f.id)) skip.add(f.id);
        prefs.edit().putStringSet(task + ".skip", skip).apply();
        for (String id : skip) resolved(task, id);
    }
    synchronized void clear(String task) {
        prefs.edit().remove(task + ".fail").remove(task + ".skip").remove(task + ".archive").remove(task + ".sealed").apply();
    }
    synchronized boolean archiving(String task) { return prefs.getBoolean(task + ".archive", false); }
    synchronized boolean sealed(String task) { return prefs.getBoolean(task + ".sealed", false); }
    synchronized void seal(String task) { prefs.edit().putBoolean(task + ".sealed", true).apply(); }
    synchronized void archive(String task) {
        List<Failure> all = failures(task);
        for (Failure f : all) if (!f.skippable) throw new IllegalStateException("密钥、播放列表或存储异常仍未解决，无法归档");
        Set<String> skip = skipped(task);
        for (Failure f : all) skip.add(f.id);
        if (!prefs.edit().putStringSet(task + ".skip", skip).putString(task + ".fail", "[]")
                .putBoolean(task + ".archive", true).commit()) throw new IllegalStateException("无法保存归档记录");
    }
}
