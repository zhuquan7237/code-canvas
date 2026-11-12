package com.nous.codecanvas.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Undo history behaviour, including the two defects that made undo feel broken.
 *
 * <p>Reported as "有时候误输入或删除了代码无法撤回，都不知道修改了什么". Two separate causes:</p>
 * <ol>
 *   <li>A large document exhausted the character budget on its first edit, so the history was
 *       trimmed down to two entries and {@code canUndo()} was false — undo did nothing at all.</li>
 *   <li>One snapshot per keystroke meant a single undo took back one character, which reads as
 *       "nothing happened".</li>
 * </ol>
 */
public class UndoRedoManagerTest {

    @Test
    public void aLargeDocumentCanStillUndo() {
        // 4MB of text: two snapshots of this used to blow the 6MB budget immediately.
        StringBuilder big = new StringBuilder(4 * 1024 * 1024);
        while (big.length() < 4 * 1024 * 1024) big.append("0123456789abcdef");
        String baseline = big.toString();

        UndoRedoManager manager = new UndoRedoManager();
        manager.resetBaseline(baseline, 0);
        assertFalse("a freshly loaded document has nothing to undo", manager.canUndo());

        manager.pushState(baseline + "x", 1);
        assertTrue("one edit must be undoable even in a 4MB document", manager.canUndo());

        UndoRedoManager.EditSnapshot back = manager.undo();
        assertNotNull(back);
        assertEquals("undo must return the baseline", baseline.length(), back.text.length());
    }

    @Test
    public void theBudgetCountsEditsNotTheWholeFile() {
        String baseline = "0123456789";
        UndoRedoManager manager = new UndoRedoManager(50, 1000);
        manager.resetBaseline(baseline, 0);
        manager.pushState(baseline + "a", 1);
        manager.pushState(baseline + "ab", 2);
        // Cost is measured above the baseline: "a" costs 1, "ab" costs 2, total 3.
        assertEquals(3, manager.totalCharsForTest());
    }

    @Test
    public void resetBaselineClearsOldHistory() {
        UndoRedoManager manager = new UndoRedoManager();
        manager.resetBaseline("loaded", 0);
        manager.pushState("loaded + edit", 5);
        assertTrue(manager.canUndo());
        manager.resetBaseline("reloaded", 0);
        assertFalse("a fresh baseline must not leave stale steps behind", manager.canUndo());
        assertFalse(manager.canRedo());
    }

    @Test
    public void undoAndRedoWalkTheHistoryInOrder() {
        UndoRedoManager manager = new UndoRedoManager();
        manager.resetBaseline("v0", 0);
        manager.pushState("v1", 1);
        manager.pushState("v2", 2);
        manager.pushState("v3", 3);

        assertEquals("v2", manager.undo().text);
        assertEquals("v1", manager.undo().text);
        assertEquals("v0", manager.undo().text);
        assertFalse("at the baseline there is nothing more to undo", manager.canUndo());

        assertEquals("v1", manager.redo().text);
        assertEquals("v2", manager.redo().text);
        assertEquals("v3", manager.redo().text);
        assertFalse(manager.canRedo());
    }

    @Test
    public void aNewEditDropsTheRedoBranch() {
        UndoRedoManager manager = new UndoRedoManager();
        manager.resetBaseline("v0", 0);
        manager.pushState("v1", 1);
        manager.pushState("v2", 2);
        manager.undo();
        assertTrue(manager.canRedo());
        manager.pushState("v2-different", 2);
        assertFalse("editing after an undo must discard the old redo branch", manager.canRedo());
    }

    @Test
    public void undoOnAnEmptyHistoryIsNullNotACrash() {
        UndoRedoManager manager = new UndoRedoManager();
        assertNull(manager.undo());
        assertNull(manager.redo());
        assertFalse(manager.canUndo());
        assertFalse(manager.canRedo());
        assertTrue(manager.isEmpty());
    }

    @Test
    public void identicalStateIsNotPushedTwice() {
        UndoRedoManager manager = new UndoRedoManager();
        manager.resetBaseline("same", 0);
        manager.pushState("same", 0);
        assertFalse("pushing the current text must not create a no-op step", manager.canUndo());
    }

    @Test
    public void historyIsCappedButStillUndoable() {
        UndoRedoManager manager = new UndoRedoManager(5);
        manager.resetBaseline("v0", 0);
        for (int i = 1; i <= 40; i++) manager.pushState("v" + i, i);
        assertTrue("the newest edits must stay undoable", manager.canUndo());
        assertTrue("history must stay bounded", manager.sizeForTest() <= 5);
    }

    @Test
    public void manyUndosWalkBackThroughEveryRetainedStep() {
        UndoRedoManager manager = new UndoRedoManager(10);
        manager.resetBaseline("v0", 0);
        for (int i = 1; i <= 6; i++) manager.pushState("v" + i, i);
        int steps = 0;
        while (manager.canUndo()) {
            manager.undo();
            steps++;
        }
        assertEquals("six edits, six undos", 6, steps);
    }
}
