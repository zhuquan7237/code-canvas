package com.nous.codecanvas.editor;

import org.junit.Test;
import static org.junit.Assert.*;

public class UndoRedoManagerTest {

    @Test
    public void testUndoRedoFlow() {
        UndoRedoManager manager = new UndoRedoManager();
        assertFalse(manager.canUndo());
        assertFalse(manager.canRedo());

        manager.pushState("Hello", 5);
        assertFalse(manager.canUndo());
        assertFalse(manager.canRedo());

        manager.pushState("Hello World", 11);
        assertTrue(manager.canUndo());
        assertFalse(manager.canRedo());

        UndoRedoManager.EditSnapshot undone = manager.undo();
        assertNotNull(undone);
        assertEquals("Hello", undone.text);
        assertEquals(5, undone.cursorPosition);
        assertFalse(manager.canUndo());
        assertTrue(manager.canRedo());

        UndoRedoManager.EditSnapshot redone = manager.redo();
        assertNotNull(redone);
        assertEquals("Hello World", redone.text);
        assertEquals(11, redone.cursorPosition);
        assertTrue(manager.canUndo());
        assertFalse(manager.canRedo());
    }

    @Test
    public void testBranchingHistoryOverwritesRedo() {
        UndoRedoManager manager = new UndoRedoManager();
        manager.pushState("A", 1);
        manager.pushState("B", 1);
        manager.undo();
        assertTrue(manager.canRedo());

        // New branch
        manager.pushState("C", 1);
        assertFalse(manager.canRedo());
        assertEquals("A", manager.undo().text);
    }
}
