package com.nous.codecanvas.editor;

import java.util.ArrayList;
import java.util.List;

public class UndoRedoManager {
    private final List<EditSnapshot> history = new ArrayList<>();
    private int currentIndex = -1;
    private final int maxHistory;

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
        this.maxHistory = maxHistory;
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
