package com.fine.player;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;
import org.json.*;

/** Durable locations for completed downloads; the Media3 index remains the library index. */
final class LibraryFiles {
    private final SharedPreferences prefs;
    LibraryFiles(Context context) { prefs = context.getSharedPreferences("library_files", 0); }
    synchronized String get(String id, String key) { return entry(id).optString(key, ""); }
    synchronized boolean flag(String id, String key) { return entry(id).optBoolean(key); }
    synchronized void put(String id, String key, Object value) throws IOException {
        JSONObject entry = entry(id);
        try { entry.put(key, value); } catch (JSONException e) { throw new IOException(e); }
        if (!prefs.edit().putString(id, entry.toString()).commit()) throw new IOException("无法保存文件记录");
    }
    synchronized void output(String id, String uri, String folder) throws IOException {
        JSONObject entry = entry(id);
        try { entry.put("hls", uri).put("folder", folder); } catch (JSONException e) { throw new IOException(e); }
        if (!prefs.edit().putString(id, entry.toString()).commit()) throw new IOException("无法保存文件记录");
    }
    synchronized void clear(String id) throws IOException {
        if (!prefs.edit().remove(id).commit()) throw new IOException("无法删除文件记录");
    }
    private JSONObject entry(String id) {
        try { return new JSONObject(prefs.getString(id, "{}")); }
        catch (JSONException e) { throw new IllegalStateException("文件记录损坏，已停止操作", e); }
    }
    String destination() { return prefs.getString("destination", ""); }
    void destination(String uri) throws IOException {
        if (!prefs.edit().putString("destination", uri).commit()) throw new IOException("无法保存下载位置");
    }
    String destinationLabel() {
        String uri = destination();
        return uri.isEmpty() ? "应用内部存储" : DocumentsContract.getTreeDocumentId(Uri.parse(uri));
    }
    static Uri treeRoot(String tree) {
        Uri uri = Uri.parse(tree);
        return DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
    }
    static Uri create(Context context, Uri parent, String mime, String name) throws IOException {
        if ("file".equals(parent.getScheme())) {
            File file = new File(parent.getPath(), name);
            boolean ok = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) ? file.mkdir() : file.createNewFile();
            if (!ok) throw new IOException("无法创建文件，请检查空间或目录权限");
            return Uri.fromFile(file);
        }
        Uri result = DocumentsContract.createDocument(context.getContentResolver(), parent, mime, name);
        if (result == null) throw new IOException("无法创建文件，请重新选择目录");
        return result;
    }
    static InputStream read(Context c, Uri uri) throws IOException {
        InputStream in = "file".equals(uri.getScheme()) ? new FileInputStream(uri.getPath()) : c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("无法读取文件");
        return in;
    }
    static OutputStream write(Context c, Uri uri) throws IOException {
        OutputStream out = "file".equals(uri.getScheme()) ? new FileOutputStream(uri.getPath()) : c.getContentResolver().openOutputStream(uri, "wt");
        if (out == null) throw new IOException("无法写入文件");
        return out;
    }
    /** Only called for exact URIs created by this app, never the user's selected parent directory. */
    static void delete(Context c, Uri uri) throws IOException {
        if ("file".equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(c, Uri.fromFile(child));
            if (file.exists() && !file.delete()) throw new IOException("文件删除失败");
        } else {
            try {
                if (!DocumentsContract.deleteDocument(c.getContentResolver(), uri)) throw new IOException("文件删除失败");
            } catch (FileNotFoundException alreadyRemoved) { /* idempotent cleanup */ }
        }
    }
    static Uri outputParent(Context c, String destination) throws IOException {
        if (!destination.isEmpty()) return treeRoot(destination);
        File folder = new File(c.getFilesDir(), "exports");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("无法创建保存目录");
        return Uri.fromFile(folder);
    }
}
