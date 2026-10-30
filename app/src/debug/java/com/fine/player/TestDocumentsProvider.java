package com.fine.player;

import android.database.*;
import android.os.*;
import android.provider.*;
import java.io.*;

/** Test-only SAF provider, isolated to the test APK's cache directory. */
public final class TestDocumentsProvider extends DocumentsProvider {
    private File root;
    @Override public boolean onCreate() { root = new File(getContext().getCacheDir(), "documents"); root.mkdirs(); return true; }
    private File file(String id) throws FileNotFoundException {
        try {
            File file = id.equals("root") ? root : new File(root, id.substring(5));
            if (!file.getCanonicalPath().equals(root.getCanonicalPath()) && !file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) throw new IOException();
            return file;
        } catch (Exception e) { throw new FileNotFoundException("Invalid test document"); }
    }
    private String id(File file) { return file.equals(root) ? "root" : "root/" + file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1); }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor c = new MatrixCursor(projection == null ? new String[]{DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS} : projection);
        c.newRow().add(DocumentsContract.Root.COLUMN_ROOT_ID, "root").add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root")
                .add(DocumentsContract.Root.COLUMN_TITLE, "Fine test only").add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE); return c;
    }
    private MatrixCursor cursor(String[] projection) {
        return new MatrixCursor(projection == null ? new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_FLAGS} : projection);
    }
    private void row(MatrixCursor c, File file) {
        c.newRow().add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id(file)).add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.getName())
                .add(DocumentsContract.Document.COLUMN_MIME_TYPE, file.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream")
                .add(DocumentsContract.Document.COLUMN_SIZE, file.length()).add(DocumentsContract.Document.COLUMN_FLAGS,
                        DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE | DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);
    }
    @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException { MatrixCursor c = cursor(projection); row(c, file(id)); return c; }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sort) throws FileNotFoundException {
        MatrixCursor c = cursor(projection); File[] children = file(parent).listFiles(); if (children != null) for (File child : children) row(c, child); return c;
    }
    @Override public boolean isChildDocument(String parent, String child) {
        try { return file(child).getAbsolutePath().startsWith(file(parent).getAbsolutePath() + File.separator); }
        catch (FileNotFoundException e) { return false; }
    }
    @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
        File child = new File(file(parent), name);
        try {
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) { if (!child.mkdir()) throw new IOException(); }
            else if (!child.createNewFile()) throw new IOException();
            return id(child);
        } catch (IOException e) { throw new FileNotFoundException("Create failed"); }
    }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException { return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode)); }
    @Override public void deleteDocument(String id) throws FileNotFoundException { remove(file(id)); }
    private void remove(File f) throws FileNotFoundException {
        File[] children = f.listFiles(); if (children != null) for (File child : children) remove(child);
        if (f.exists() && !f.delete()) throw new FileNotFoundException("Delete failed");
    }
}
