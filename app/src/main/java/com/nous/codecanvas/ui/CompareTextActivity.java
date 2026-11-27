package com.nous.codecanvas.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.BidiFormatter;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.util.AppearanceManager;
import com.nous.codecanvas.util.TextComparison;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays changes between two saved text snapshots. Never opens an editor or writes the library. */
public final class CompareTextActivity extends Activity {
    static final String EXTRA_LEFT = "compare_left";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView left, right, status, eof, policy;
    private View choose, swap;
    private ListView list;
    private List<Entry> snapshot;
    private Entry before, after;

    private static final class Entry {
        final String id, title;
        final int length;
        final long updated;
        Entry(CanvasDocument doc) {
            id = doc.getId();
            title = doc.getTitle();
            length = TextComparison.characterCount(doc.getContent());
            updated = doc.getUpdatedAt();
        }
    }
    private int generation;
    private boolean firstPicker = true;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppearanceManager.wrap(base));
    }

    static boolean accepts(CanvasDocument doc) {
        return doc != null && !doc.isBlob() && doc.kind().editable();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        setContentView(R.layout.activity_compare_text);
        findViewById(R.id.root_compare).setOnApplyWindowInsetsListener((v, insets) -> {
            SystemBarInsets.apply(v, insets); return insets;
        });
        list = findViewById(R.id.compare_list);
        View header = getLayoutInflater().inflate(R.layout.header_compare_text, list, false);
        left = header.findViewById(R.id.compare_left);
        right = header.findViewById(R.id.compare_right);
        status = header.findViewById(R.id.compare_status);
        eof = header.findViewById(R.id.compare_eof);
        policy = header.findViewById(R.id.compare_policy);
        choose = header.findViewById(R.id.btn_compare_choose);
        swap = header.findViewById(R.id.btn_compare_swap);
        list.addHeaderView(header, null, false);
        bindRows(java.util.Collections.emptyList());
        findViewById(R.id.btn_compare_close).setOnClickListener(v -> finish());
        choose.setOnClickListener(v -> pick());
        swap.setOnClickListener(v -> {
            if (after == null) return;
            Entry tmp = before; before = after; after = tmp;
            compare();
        });
        choose.setEnabled(false);
        swap.setEnabled(false);
        String a = state == null ? getIntent().getStringExtra(EXTRA_LEFT) : state.getString("a");
        String b = state == null ? null : state.getString("b");
        worker.execute(() -> {
            try {
                List<Entry> read = new ArrayList<>();
                for (CanvasDocument doc : new DocumentRepository(getApplicationContext()).getAllDocuments()) {
                    if (accepts(doc)) read.add(new Entry(doc));
                }
                runOnUiThread(() -> bindSnapshot(read, a, b));
            } catch (Exception failure) {
                runOnUiThread(() -> { if (alive()) status.setText(R.string.compare_load_failed); });
            }
        });
    }

    private void bindSnapshot(List<Entry> read, String a, String b) {
        if (!alive()) return;
        snapshot = read;
        before = find(a);
        after = find(b);
        if (before == null) {
            status.setText(R.string.compare_missing);
            return;
        }
        if (after != null && !after.id.equals(before.id)) {
            compare();
        } else {
            after = null;
            bindNames();
            status.setText(b == null ? R.string.compare_pick_hint : R.string.compare_missing_other);
            if (firstPicker && b == null && hasWindowFocus()) { firstPicker = false; pick(); }
        }
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && firstPicker && before != null && after == null) { firstPicker = false; pick(); }
    }

    private Entry find(String id) {
        if (id != null) for (Entry entry : snapshot) if (id.equals(entry.id)) return entry;
        return null;
    }

    private void pick() {
        if (before == null || !alive()) return;
        List<Entry> others = new ArrayList<>();
        for (Entry doc : snapshot) if (!before.id.equals(doc.id)) others.add(doc);
        if (others.isEmpty()) {
            if (after == null) status.setText(R.string.compare_no_candidates);
            return;
        }
        ArrayAdapter<Entry> options = new ArrayAdapter<Entry>(
                this, android.R.layout.simple_list_item_1, others) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView label = (TextView) super.getView(position, recycled, parent);
                label.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch_min));
                label.setSingleLine(false);
                label.setMaxLines(4);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setText(CompareTextActivity.this.label(getItem(position)));
                return label;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.compare_pick_title)
                .setAdapter(options, (dialog, which) -> {
                    after = others.get(which);
                    compare();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String label(Entry doc) {
        String id = doc.id;
        String title = doc.title.replace('\r',' ').replace('\n',' ');
        if (title.length() > 120) title = title.substring(0, title.offsetByCodePoints(0,
                Math.min(120, title.codePointCount(0, title.length())))) + "…";
        String shortId = id.substring(Math.max(0, id.length()-8));
        // The identifier goes first so narrow screens cannot obscure same-name distinctions.
        return "[" + shortId + "] " + BidiFormatter.getInstance().unicodeWrap(title) + "\n"
                + getString(R.string.compare_file_detail,
                   DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                        .format(new Date(doc.updated)), doc.length);
    }

    private void bindNames() {
        left.setText(getString(R.string.compare_base, label(before)));
        right.setText(after == null ? null : getString(R.string.compare_revision, label(after)));
        right.setVisibility(after == null ? View.GONE : View.VISIBLE);
        choose.setEnabled(true);
        swap.setEnabled(after != null);
    }

    private void compare() {
        if (before == null || after == null || !alive()) return;
        firstPicker = false;
        final int run = ++generation;
        String a = before.id, b = after.id;
        bindNames();
        status.setText(R.string.compare_loading);
        eof.setVisibility(View.GONE);
        policy.setVisibility(View.GONE);
        choose.setEnabled(false);
        swap.setEnabled(false);
        bindRows(java.util.Collections.emptyList());
        worker.execute(() -> {
            try {
                CanvasDocument leftDoc = null, rightDoc = null;
                for (CanvasDocument doc : new DocumentRepository(getApplicationContext()).getAllDocuments()) {
                    if (a.equals(doc.getId())) leftDoc = doc;
                    if (b.equals(doc.getId())) rightDoc = doc;
                }
                if (!accepts(leftDoc) || !accepts(rightDoc)) {
                    final boolean leftMissing = !accepts(leftDoc);
                    runOnUiThread(() -> {
                        if (alive() && run == generation) {
                            if (leftMissing) { before = null; choose.setEnabled(false); }
                            else { snapshot.removeIf(entry -> entry.id.equals(b)); }
                            after = null;
                            swap.setEnabled(false);
                            choose.setEnabled(!leftMissing);
                            right.setVisibility(View.GONE);
                            status.setText(leftMissing ? R.string.compare_missing : R.string.compare_missing_other);
                        }
                    });
                    return;
                }
                TextComparison.Result result = TextComparison.compare(leftDoc.getContent(), rightDoc.getContent());
                Entry actualLeft = new Entry(leftDoc), actualRight = new Entry(rightDoc);
                runOnUiThread(() -> {
                    if (alive() && run == generation) {
                        before = actualLeft; after = actualRight; bindNames(); show(result);
                    }
                });
            } catch (TextComparison.LimitExceeded limit) {
                runOnUiThread(() -> {
                    if (alive() && run == generation) {
                        choose.setEnabled(true);
                        swap.setEnabled(true);
                        status.setText(R.string.compare_limit);
                    }
                });
            } catch (Exception unavailable) {
                runOnUiThread(() -> {
                    if (alive() && run == generation) {
                        choose.setEnabled(true);
                        swap.setEnabled(true);
                        status.setText(R.string.compare_load_failed);
                    }
                });
            }
        });
    }

    private void show(TextComparison.Result result) {
        status.setText(result.same() ? getString(R.string.compare_identical)
                : result.changes.isEmpty() ? getString(R.string.compare_eof_only)
                : getString(R.string.compare_counts, result.added, result.removed));
        policy.setVisibility(result.changes.isEmpty() ? View.GONE : View.VISIBLE);
        boolean changedEof = result.leftEndsWithNewline != result.rightEndsWithNewline;
        eof.setVisibility(changedEof ? View.VISIBLE : View.GONE);
        if (changedEof) eof.setText(result.rightEndsWithNewline
                ? R.string.compare_eof_added : R.string.compare_eof_removed);
        bindRows(result.changes);
    }

    private void bindRows(List<TextComparison.Row> rows) {
        list.setAdapter(new ArrayAdapter<TextComparison.Row>(this, android.R.layout.simple_list_item_1,
                rows) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row;
                if (recycled instanceof TextView) row = (TextView) recycled;
                else {
                    row = new TextView(CompareTextActivity.this);
                    float density = getResources().getDisplayMetrics().density;
                    row.setPadding(Math.round(16*density), Math.round(8*density),
                            Math.round(16*density), Math.round(8*density));
                    row.setTypeface(android.graphics.Typeface.MONOSPACE);
                    row.setTextSize(14);
                    row.setSaveEnabled(false);
                    row.setBreakStrategy(0);
                    row.setTextDirection(View.TEXT_DIRECTION_LTR);
                }
                TextComparison.Row change = getItem(position);
                String body = TextComparison.visibleTrailingWhitespace(change.text);
                row.setBackgroundColor(getColor(change.added
                        ? R.color.canvas_success_surface : R.color.canvas_danger_surface));
                row.setTextColor(getColor(R.color.canvas_ink_primary));
                row.setText((change.added ? "+ " : "− ") + change.number + "  "
                        + BidiFormatter.getInstance().unicodeWrap(body));
                row.setContentDescription(getString(change.added
                        ? R.string.compare_added_line : R.string.compare_removed_line, change.number, body));
                return row;
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("a", before != null ? before.id : getIntent().getStringExtra(EXTRA_LEFT));
        if (after != null) state.putString("b", after.id);
    }

    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
