package com.reecedunn.espeak;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test-APK-only document provider; never included in a release engine. */
public final class TestDictionaryProvider extends ContentProvider {
    private volatile CountDownLatch readGate;
    private volatile CountDownLatch entered;
    private volatile boolean failRead;
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME });
        cursor.addRow(new Object[] { uri.getLastPathSegment() });
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if ("unavailable_dict".equals(uri.getLastPathSegment())) throw new FileNotFoundException("Fixture unavailable");
        CountDownLatch gate = readGate;
        if (!mode.contains("w") && gate != null) {
            entered.countDown();
            try {
                if (!gate.await(10, TimeUnit.SECONDS)) throw new FileNotFoundException("Fixture read deadline");
            } catch (InterruptedException failure) {
                throw new FileNotFoundException("Fixture read interrupted");
            }
            if (failRead) throw new FileNotFoundException("Injected ordinary provider read failure");
        }
        int flags = mode.contains("w")
            ? ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY
            : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(), "r42-valid-pl-dict"), flags);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) {
        File file = new File(getContext().getCacheDir(), "r42-valid-pl-dict");
        return file.delete() ? 1 : 0;
    }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public Bundle call(String method, String argument, Bundle extras) {
        Bundle result = new Bundle();
        if ("holdRead".equals(method)) {
            entered = new CountDownLatch(1);
            failRead = "failure".equals(argument);
            readGate = new CountDownLatch(1);
        } else if ("readEntered".equals(method)) {
            result.putBoolean("entered", entered != null && entered.getCount() == 0);
        } else if ("releaseRead".equals(method)) {
            CountDownLatch gate = readGate;
            if (gate != null) gate.countDown();
        } else if ("resetRead".equals(method)) {
            CountDownLatch gate = readGate;
            if (gate != null) gate.countDown();
            readGate = null;
            entered = null;
            failRead = false;
        }
        return result;
    }
}
