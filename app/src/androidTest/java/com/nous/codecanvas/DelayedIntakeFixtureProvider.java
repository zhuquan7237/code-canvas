package com.nous.codecanvas;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test APK only. Serves one synthetic string; requests never access local files. */
public final class DelayedIntakeFixtureProvider extends ContentProvider {
    private volatile CountDownLatch gate = new CountDownLatch(1);
    private final AtomicInteger readers = new AtomicInteger();
    private static final byte[] BYTES = "finished after recreation".getBytes(StandardCharsets.UTF_8);

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public Cursor query(Uri uri, String[] projection, String select, String[] args, String order) {
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String col : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) row.add(uri.getLastPathSegment());
            else if (OpenableColumns.SIZE.equals(col)) row.add(BYTES.length);
            else row.add(null);
        }
        return cursor;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("release".equals(method)) gate.countDown();
        if ("reset".equals(method)) {
            gate.countDown();
            gate = new CountDownLatch(1);
            readers.set(0);
        }
        Bundle result = new Bundle();
        result.putInt("readers", readers.get());
        return result;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            CountDownLatch wait = gate;
            readers.incrementAndGet();
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    if (wait.await(20, TimeUnit.SECONDS)) output.write(BYTES);
                } catch (IOException ignored) {
                    // The fixture's consumer closed the stream.
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, "delayed-intake-fixture").start();
            return pipe[0];
        } catch (IOException e) {
            throw new FileNotFoundException("Fixture could not create a pipe");
        }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String select, String[] args) { return 0; }
    @Override public int delete(Uri uri, String select, String[] args) { return 0; }
}

