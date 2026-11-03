package com.fine.player;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App-private local history; all application database calls run on one worker. */
final class BrowserHistory extends SQLiteOpenHelper {
    static final int LIMIT = 1000;
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static BrowserHistory instance;
    private final SharedPreferences preferences;
    static synchronized BrowserHistory get(Context context) {
        if (instance == null) instance = new BrowserHistory(context.getApplicationContext());
        return instance;
    }
    BrowserHistory(Context context) {
        super(context, "browser_history.db", null, 1);
        preferences = context.getSharedPreferences("browser_history", 0);
    }
    static final class Visit {
        final String url, title;
        final long time;
        Visit(String url, String title, long time) { this.url = url; this.title = title; this.time = time; }
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE visits (url TEXT PRIMARY KEY, title TEXT NOT NULL, visited INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX visits_recent ON visits(visited DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    boolean enabled() { return preferences.getBoolean("enabled", true); }
    void enabled(boolean enabled) { preferences.edit().putBoolean("enabled", enabled).apply(); }
    void visit(String value, long time) {
        if (!enabled() || value == null || value.length() > 16384) return;
        final String url;
        try { url = Links.normalize(value); } catch (IllegalArgumentException e) { return; }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues(); values.put("visited", time);
            if (db.update("visits", values, "url = ?", new String[]{url}) == 0) {
                values.put("url", url); values.put("title", ""); db.insertOrThrow("visits", null, values);
            }
            db.execSQL("DELETE FROM visits WHERE url IN (SELECT url FROM visits ORDER BY visited DESC, rowid DESC LIMIT -1 OFFSET " + LIMIT + ")");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void title(String url, String title) {
        if (!enabled() || url == null || title == null || title.trim().isEmpty()) return;
        ContentValues values = new ContentValues(); values.put("title", title.substring(0, Math.min(200, title.length())));
        // Do not recreate entries after the user has deleted or cleared them.
        getWritableDatabase().update("visits", values, "url = ?", new String[]{url});
    }
    List<Visit> search(String query) {
        List<Visit> visits = new ArrayList<>();
        String term = query.trim();
        String selection = term.isEmpty() ? null : "instr(lower(title), lower(?)) > 0 OR instr(lower(url), lower(?)) > 0";
        try (Cursor cursor = getReadableDatabase().query("visits", new String[]{"url", "title", "visited"},
                selection, term.isEmpty() ? null : new String[]{term, term}, null, null, "visited DESC, rowid DESC", String.valueOf(LIMIT))) {
            while (cursor.moveToNext()) visits.add(new Visit(cursor.getString(0), cursor.getString(1), cursor.getLong(2)));
        }
        return visits;
    }
    void delete(String url) { getWritableDatabase().delete("visits", "url = ?", new String[]{url}); }
    void clear() { getWritableDatabase().delete("visits", null, null); }
}
