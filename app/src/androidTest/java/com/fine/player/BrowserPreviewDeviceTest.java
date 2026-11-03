package com.fine.player;

import android.app.Instrumentation;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Real-device check: the existing four-second HLS fixture must yield duration and a frame. */
public class BrowserPreviewDeviceTest {
    @Test public void previewReadsDurationAndCapturesRealFrame() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        // MIUI blocks background activity launches: open the exported browser normally first.
        try (android.os.ParcelFileDescriptor launched = instrumentation.getUiAutomation()
                .executeShellCommand("am start -W -n com.fine.player/.BrowserActivity");
             InputStream output = new android.os.ParcelFileDescriptor.AutoCloseInputStream(launched)) {
            byte[] buffer = new byte[1024]; while (output.read(buffer) != -1) { }
        }
        ExecutorService worker = Executors.newCachedThreadPool();
        MediaPreviewActivity activity = null;
        try (ServerSocket server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            worker.submit(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        worker.submit(() -> {
                            try (Socket connection = socket) {
                                connection.setSoTimeout(5000);
                                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                                String first = reader.readLine(); if (first == null) return;
                                String path = first.split(" ")[1];
                                String line; while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                                String name = path.substring(path.lastIndexOf('/') + 1).split("\\?")[0];
                                if (!name.matches("test(?:[01]\\.ts|\\.m3u8)")) return;
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                try (InputStream input = instrumentation.getContext().getAssets().open("hls/" + name)) {
                                    byte[] buffer = new byte[8192]; int count;
                                    while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                                }
                                OutputStream out = connection.getOutputStream();
                                String mime = name.endsWith("m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t";
                                out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + mime + "\r\nContent-Length: " + bytes.size() + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                                bytes.writeTo(out); out.flush();
                            } catch (IOException ignored) { }
                        });
                    } catch (IOException closed) { break; }
                }
            });
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/test.m3u8";
            instrumentation.runOnMainSync(MediaPreviews::clear);
            Intent intent = MediaPreviewActivity.intent(instrumentation.getTargetContext(), new BrowserResources.Entry(url, "HLS"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MediaPreviewActivity)instrumentation.startActivitySync(intent);
            boolean[] ready = {false};
            long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
            while (!ready[0] && android.os.SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync(() -> ready[0] = MediaPreviews.info(url).duration >= 3800 && MediaPreviews.image(url) != null);
                Thread.sleep(100);
            }
            assertTrue("Expected HLS duration and decoded thumbnail", ready[0]);
        } finally {
            if (activity != null) { MediaPreviewActivity finished = activity; instrumentation.runOnMainSync(finished::finish); }
            worker.shutdownNow();
        }
    }
}
