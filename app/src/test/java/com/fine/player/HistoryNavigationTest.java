package com.fine.player;

import android.content.Intent;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class HistoryNavigationTest {
    @Test public void searchAndOpenReturnExactUrlToExistingBrowser() throws Exception {
        BrowserHistory history = BrowserHistory.get(RuntimeEnvironment.getApplication());
        history.clear(); history.enabled(true);
        String url = "https://example.com/watch?token=a%2Fb";
        history.visit(url, 1); history.title(url, "目标视频"); history.visit("https://example.com/other", 2);
        try (ActivityController<HistoryActivity> controller = Robolectric.buildActivity(HistoryActivity.class).create()) {
            HistoryActivity activity = controller.get();
            BrowserHistory.WORKER.submit(() -> {}).get(); shadowOf(android.os.Looper.getMainLooper()).idle();
            ListView list = activity.findViewById(R.id.history_list);
            assertEquals(2, list.getCount());
            ((EditText)activity.findViewById(R.id.history_search)).setText("目标");
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
            BrowserHistory.WORKER.submit(() -> {}).get(); shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1, list.getCount()); list.performItemClick(null, 0, 0);
            Intent intent = shadowOf(activity).getNextStartedActivity();
            assertEquals(url, intent.getDataString());
            assertEquals(BrowserActivity.class.getName(), intent.getComponent().getClassName());
            assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
        }
    }
}
