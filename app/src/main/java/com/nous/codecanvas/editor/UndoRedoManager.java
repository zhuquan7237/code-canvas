package com.nous.codecanvas.editor;

import java.util.ArrayList;
import java.util.List;

public class UndoRedoManager {
    private final List<EditSnapshot> history = new ArrayList<>();
    private int currentIndex = -1;
    private final int maxHistory;
    /**
     * Total characters of history we are willing to hold. Fifty snapshots of a 2 MiB document is
     * 100 MiB of copies, which on a low-end phone is the difference between "undo works" and the
     * process being killed. Old entries are dropped once this budget is exceeded.
     */
    private final long maxTotalChars;
    private static final long DEFAULT_MAX_TOTAL_CHARS = 6L * 1024L * 1024L;

    public static class EditSnapshot {
        public final String text;
        public final int cursorPosition;

        public EditSnapshot(String text, int cursorPosition) {
            this.text = text != null ? text : "";
            this.cursorPosition = cursorPosition;
        }
    }

    public UndoRedoManager() {
        this(50);
    }

    public UndoRedoManager(int maxHistory) {
        this(maxHistory, DEFAULT_MAX_TOTAL_CHARS);
    }

    public UndoRedoManager(int maxHistory, long maxTotalChars) {
        this.maxHistory = maxHistory;
        this.maxTotalChars = maxTotalChars;
    }

    public synchronized void pushState(String text, int cursorPosition) {
        if (currentIndex >= 0 && currentIndex < history.size()) {
            EditSnapshot current = history.get(currentIndex);
            if (current.text.equals(text)) {
                return;
            }
        }

        // Trim any redo steps
        while (history.size() > currentIndex + 1) {
            history.remove(history.size() - 1);
        }

        history.add(new EditSnapshot(text, cursorPosition));
        currentIndex = history.size() - 1;
        while (history.size() > maxHistory) {
            history.remove(0);
            currentIndex--;
        }

        // Keep the newest edits and drop the oldest until the character budget fits. Dropping from
        // the front shifts every index down, so the cursor index follows.
        long total = 0;
        for (EditSnapshot snapshot : history) {
            total += snapshot.text.length();
        }
        while (history.size() > 2 && total > maxTotalChars) {
            total -= history.get(0).text.length();
            history.remove(0);
            currentIndex--;
        }
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        if (currentIndex >= history.size()) {
            currentIndex = history.size() - 1;
        }
    }

    /**
     * Replace the baseline snapshot — the state "before any edit" — with the text on disk.
     *
     * <p>Without this, a large document exhausts the character budget on its first keystroke (two
     * snapshots of a 4MB file already exceed 6MB), the budget loop trims the history down to the
     * two newest entries, and undo silently does nothing. The user's report — "误输入或删除了代码
     * 无法撤回" — is exactly that. Keeping the baseline as a reference to the saved text (not a
     * copy) means the budget measures the <i>edits</i>, not the file.</p>
     */
    public synchronized void resetBaseline(String text, int cursorPosition) {
        history.clear();
        history.add(new EditSnapshot(text, cursorPosition));
        currentIndex = 0;
    }

    /** True when the history has nothing to undo — the UI uses this to say so instead of doing nothing. */
    public synchronized boolean isEmpty() {
        return history.isEmpty();
    }

    /**
     * Characters currently held in history, measured as the cost above the baseline snapshot.
     *
     * <p>The baseline is the document as it was loaded; it would be held in memory anyway, so
     * charging it to the undo budget is what made a 4MB file unable to undo a single character.</p>
     */
    public synchronized long totalCharsForTest() {
        if (history.isEmpty()) return 0;
        long baseline = history.get(0).text.length();
        long total = 0;
        for (EditSnapshot snapshot : history) {
            total += Math.max(0, snapshot.text.length() - baseline);
        }
        return total;
    }

    public synchronized int sizeForTest() {
        return history.size();
    }

    public synchronized boolean canUndo() {
        return currentIndex > 0;
    }

    public synchronized boolean canRedo() {
        return currentIndex >= 0 && currentIndex < history.size() - 1;
    }

    public synchronized EditSnapshot undo() {
        if (!canUndo()) return null;
        currentIndex--;
        return history.get(currentIndex);
    }

    public synchronized EditSnapshot redo() {
        if (!canRedo()) return null;
        currentIndex++;
        return history.get(currentIndex);
    }
}
