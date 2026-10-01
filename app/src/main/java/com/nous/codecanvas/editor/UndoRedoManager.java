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
        if (history.size() > maxHistory) {
            history.remove(0);
        } else {
            currentIndex++;
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

    /** Characters currently held in history — used to prove the budget is respected. */
    public synchronized long totalCharsForTest() {
        long total = 0;
        for (EditSnapshot snapshot : history) {
            total += snapshot.text.length();
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
