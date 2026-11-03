package com.fine.player;

import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class BrowserHistoryTest {
    private Context context;
    private BrowserHistory history;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("browser_history.db"); context.getSharedPreferences("browser_history", 0).edit().clear().commit();
        history = new BrowserHistory(context);
    }
    @After public void close() { history.close(); }
    @Test public void historySurvivesReopenAndRevisitsMoveToTopWithoutDuplicating() {
        String signed = "https://example.com/watch?sig=a%2Fb+z";
        history.visit(signed, 1); history.title(signed, "演示视频");
        history.visit("https://example.com/next", 2); history.visit(signed, 3);
        history.close(); history = new BrowserHistory(context);
        assertEquals(2, history.search("").size());
        assertEquals(signed, history.search("").get(0).url);
        assertEquals("演示视频", history.search("").get(0).title);
        assertEquals(3, history.search("").get(0).time);
    }
    @Test public void searchTreatsQuotesAndWildcardsLiterally() {
        history.visit("https://example.com/a?q=%25", 1); history.title("https://example.com/a?q=%25", "100%_测试");
        history.visit("https://example.com/b", 2); history.title("https://example.com/b", "OTHER");
        assertEquals(1, history.search("%_").size());
        assertEquals(1, history.search("测试").size());
        assertEquals(1, history.search("other").size());
        assertTrue(history.search("' OR 1=1 --").isEmpty());
    }
    @Test public void disabledRecordingAndDeletesDoNotResurrectEntriesOnTitleCallbacks() {
        String url = "https://example.com/one";
        history.visit(url, 1); history.enabled(false); history.visit("https://example.com/two", 2);
        assertEquals(1, history.search("").size());
        history.enabled(true); history.delete(url); history.title(url, "late callback");
        assertTrue(history.search("").isEmpty());
        history.visit(url, 3); history.clear(); history.title(url, "late callback");
        assertTrue(history.search("").isEmpty());
        for (String invalid : new String[]{"file:///data/test", "about:blank", "javascript:alert(1)", "https://user:pass@example.com/"}) history.visit(invalid, 4);
        assertTrue(history.search("").isEmpty());
    }
    @Test public void retentionKeepsOnlyNewestThousandUrls() {
        android.database.sqlite.SQLiteDatabase db = history.getWritableDatabase(); db.beginTransaction();
        try {
            for (int i=0;i<1000;i++) db.execSQL("INSERT INTO visits VALUES (?, '', ?)", new Object[]{"https://example.com/"+i, i});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        history.visit("https://example.com/new", 1001);
        assertEquals(1000, history.search("").size());
        assertEquals("https://example.com/new", history.search("").get(0).url);
        assertFalse(history.search("").stream().anyMatch(v -> v.url.equals("https://example.com/0")));
    }
}
