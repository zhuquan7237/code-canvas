package com.nous.codecanvas.data;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One batch of selected files. Display names are not identity, and only content URIs are read. */
public final class ImportBatch {
    public static final int MAX_FILES = 8;
    private static final int MAX_ITEMS_INSPECTED = 256;

    public final List<Uri> files;
    public final List<Integer> positions;
    public final int duplicates;
    public final int invalid;
    public final int overflow;
    public final boolean grouped;

    private ImportBatch(Collector c, boolean grouped) {
        this.files = Collections.unmodifiableList(new ArrayList<>(c.files));
        this.positions = Collections.unmodifiableList(new ArrayList<>(c.positions));
        this.duplicates = c.duplicates;
        this.invalid = c.invalid;
        this.overflow = c.overflow;
        this.grouped = grouped;
    }

    /**
     * In a share, EXTRA_STREAM is authoritative when present. ClipData ordinarily repeats those
     * URIs for the read grants, not a second list to import. Some senders only use ClipData.
     */
    public static ImportBatch fromShare(Intent intent) {
        Collector c = new Collector();
        try {
            ArrayList<?> stream = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (stream != null && !stream.isEmpty()) {
                int inspected = Math.min(stream.size(), MAX_ITEMS_INSPECTED);
                for (int i = 0; i < inspected; i++) c.add(stream.get(i));
                c.overflow += stream.size() - inspected;
                if (!c.files.isEmpty() || intent.getClipData() == null) return new ImportBatch(c, true);
                c.ordinal = 0; // ClipData is a separate original list.
            }
        } catch (RuntimeException e) {
            c.invalid++;
        }
        c.addClip(intent.getClipData());
        return new ImportBatch(c, true);
    }

    public static ImportBatch fromPicker(Intent intent) {
        Collector c = new Collector();
        ClipData clip = intent.getClipData();
        if (clip != null && clip.getItemCount() > 1) {
            c.addClip(clip);
            return new ImportBatch(c, true);
        }
        Uri data = intent.getData();
        if (data != null) c.add(data);
        else c.addClip(clip);
        return new ImportBatch(c, false);
    }

    private static final class Collector {
        final List<Uri> files = new ArrayList<>();
        final List<Integer> positions = new ArrayList<>();
        final Set<Uri> seen = new HashSet<>();
        int ordinal, duplicates, invalid, overflow;

        void addClip(ClipData clip) {
            if (clip == null) return;
            int inspected = Math.min(clip.getItemCount(), MAX_ITEMS_INSPECTED);
            for (int i = 0; i < inspected; i++) add(clip.getItemAt(i).getUri());
            overflow += clip.getItemCount() - inspected;
        }

        void add(Object value) {
            ordinal++;
            if (!(value instanceof Uri)) {
                invalid++;
                return;
            }
            Uri uri = (Uri) value;
            if (!"content".equals(uri.getScheme()) || uri.getAuthority() == null
                    || uri.getAuthority().isEmpty()) {
                invalid++;
                return;
            }
            if (!seen.add(uri)) duplicates++;
            else if (files.size() < MAX_FILES) {
                files.add(uri);
                positions.add(ordinal);
            }
            else overflow++;
        }
    }
}
