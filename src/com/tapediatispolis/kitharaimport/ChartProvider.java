package com.tapediatispolis.kitharaimport;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public final class ChartProvider extends ContentProvider {
    static final String AUTHORITY = "com.tapediatispolis.kitharaimport.charts";
    static final String DIRECTORY = "charts";
    static final String MIME_TYPE = "text/plain";

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return MIME_TYPE; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        String[] columns = projection == null
                ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        try {
            File file = findChart(uri);
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String column : columns) {
                if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
                else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
                else row.add(null);
            }
        } catch (FileNotFoundException ignored) {
            // A pending URI can expire between imports.
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Charts are read only.");
        return ParcelFileDescriptor.open(findChart(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File findChart(Uri uri) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 2 || !segments.get(0).matches("[0-9a-f-]{36}")) {
            throw new FileNotFoundException("Chart not found.");
        }
        String filename = segments.get(1);
        if (!filename.endsWith(".chordpro") || filename.contains("/") || filename.contains("\\")) {
            throw new FileNotFoundException("Chart not found.");
        }
        try {
            File root = new File(getContext().getCacheDir(), DIRECTORY).getCanonicalFile();
            File folder = new File(root, segments.get(0));
            File file = new File(folder, filename).getCanonicalFile();
            if (folder.equals(file.getParentFile()) && file.isFile()) return file;
        } catch (IOException ignored) {
            // Treat an unreadable chart as missing.
        }
        throw new FileNotFoundException("Chart not found.");
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
