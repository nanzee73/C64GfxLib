package de.c64gfx.swing;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UndoHistoryTest {
    private int contrast, gamma = 100;

    private UndoHistory history() {
        return new UndoHistory(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contrast", contrast);
            m.put("gamma", gamma);
            return m;
        }, m -> {
            contrast = (Integer) m.get("contrast");
            gamma = (Integer) m.get("gamma");
        });
    }

    @Test
    void undoAndRedoStepByStep() {
        UndoHistory h = history();
        h.reset();
        assertFalse(h.canUndo());
        contrast = 10; h.changed(); contrast = 20; h.changed(); h.commit();   // one drag = one step
        gamma = 150; h.commit();
        assertEquals("gamma", UndoHistory.difference(Map.of("contrast", 20, "gamma", 100), Map.of("contrast", 20, "gamma", 150)));
        h.undo();
        assertEquals(100, gamma);
        assertEquals(20, contrast);
        h.undo();
        assertEquals(0, contrast);
        assertFalse(h.canUndo());
        h.redo();
        assertEquals(20, contrast);
        h.redo();
        assertEquals(150, gamma);
        assertFalse(h.canRedo());
    }

    @Test
    void aNewChangeDropsTheRedoStepsAndTheLimitHolds() {
        UndoHistory h = history();
        h.reset();
        contrast = 1; h.commit();
        h.undo();
        assertTrue(h.canRedo());
        gamma = 120; h.commit();
        assertFalse(h.canRedo());
        for (int i = 0; i < UndoHistory.LIMIT + 50; i++) { contrast = i + 2; h.commit(); }
        int steps = 0;
        while (h.canUndo()) { h.undo(); steps++; }
        assertEquals(UndoHistory.LIMIT, steps);
    }

    @Test
    void resetForgetsEverything() {
        UndoHistory h = history();
        h.reset();
        contrast = 5; h.commit();
        h.reset();
        assertFalse(h.canUndo());
        assertFalse(h.canRedo());
    }
}
