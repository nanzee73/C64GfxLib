package de.c64gfx.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Undo / redo of settings and edits, by snapshots.
 * <p>
 * The application describes its state as a map from a translation key (the label of the setting, e.g.
 * {@code "src.contrast"}) to an immutable value with a working {@code equals}. Whenever something may have changed,
 * it calls {@link #changed()}; after a short pause the new state is compared with the last one and, if different,
 * becomes one undo step. So dragging a slider or painting a stroke is one step, not one per value. The menu text names
 * the setting that changed ("Undo: Contrast").
 * <p>
 * Not meant for the picture itself: after loading a new picture call {@link #reset()}.
 */
public final class UndoHistory {
    /** Number of steps kept. */
    public static final int LIMIT = 100;
    private static final int PAUSE_MS = 500;

    private final Supplier<Map<String, Object>> capture;
    private final Consumer<Map<String, Object>> restore;
    private final Deque<Map<String, Object>> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Timer pause = new Timer(PAUSE_MS, e -> commit());
    private Map<String, Object> current;
    private boolean restoring;

    /**
     * @param capture returns the current state (a new map each time)
     * @param restore sets the application to a state captured before
     */
    public UndoHistory(Supplier<Map<String, Object>> capture, Consumer<Map<String, Object>> restore) {
        this.capture = capture;
        this.restore = restore;
        pause.setRepeats(false);
    }

    /** Forgets all steps; the current state becomes the starting point (after loading a new picture). */
    public void reset() {
        pause.stop();
        undo.clear();
        redo.clear();
        current = new LinkedHashMap<>(capture.get());
        fire();
    }

    /** Something may have changed: becomes an undo step once nothing changed for a moment. */
    public void changed() {
        if (restoring || current == null) return;
        pause.restart();
        fire();                                   // Ctrl+Z works at once, even before the pause is over
    }

    /** The current state (pending changes recorded first), e.g. to compare with a saved project. */
    public Map<String, Object> state() {
        commit();
        return current == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(current));
    }

    /** True while a state is being restored – the application should not record changes then. */
    public boolean isRestoring() { return restoring; }

    /** Records a pending change now (e.g. after a dialog was confirmed). */
    public void commit() {
        pause.stop();
        if (restoring || current == null) return;
        Map<String, Object> now = new LinkedHashMap<>(capture.get());
        if (now.equals(current)) return;
        undo.push(current);
        while (undo.size() > LIMIT) undo.removeLast();
        redo.clear();
        current = now;
        fire();
    }

    public boolean canUndo() {
        commit();
        return !undo.isEmpty();
    }

    public boolean canRedo() { return !redo.isEmpty(); }

    public void undo() {
        commit();
        if (undo.isEmpty()) return;
        redo.push(current);
        apply(undo.pop());
    }

    public void redo() {
        commit();
        if (redo.isEmpty()) return;
        undo.push(current);
        apply(redo.pop());
    }

    private void apply(Map<String, Object> state) {
        current = state;
        restoring = true;
        try {
            restore.accept(state);
        } finally {
            restoring = false;
        }
        fire();
    }

    /** Translation key of the first setting that differs between two states, or null. */
    static String difference(Map<String, Object> a, Map<String, Object> b) {
        if (a == null || b == null) return null;
        for (Map.Entry<String, Object> e : a.entrySet())
            if (!Objects.equals(e.getValue(), b.get(e.getKey()))) return e.getKey();
        for (String k : b.keySet()) if (!a.containsKey(k)) return k;
        return null;
    }

    /** Readable name of the setting the next undo / redo changes, or "" if unknown. */
    public String undoName() { return name(undo.peek(), current); }

    public String redoName() { return name(current, redo.peek()); }

    private static String name(Map<String, Object> a, Map<String, Object> b) {
        String key = difference(a, b);
        if (key == null || !I18n.has(key)) return "";
        String t = I18n.tr(key).replace("…", "").replace(":", "").replaceAll("\\s+", " ").trim();
        return t.length() > 40 ? t.substring(0, 39) + "…" : t;
    }

    public void addListener(Runnable r) { listeners.add(r); }

    private void fire() { for (Runnable r : listeners) r.run(); }

    /** Calls {@link #changed()} whenever one of the components changes (sliders, lists, boxes, buttons, spinners). */
    public void watch(Component... components) {
        for (Component c : components) {
            if (c instanceof JSlider s) s.addChangeListener(e -> changed());
            else if (c instanceof JSpinner s) s.addChangeListener(e -> changed());
            else if (c instanceof JComboBox<?> b) b.addActionListener(e -> changed());
            else if (c instanceof AbstractButton b) b.addActionListener(e -> changed());
        }
    }

    /**
     * "Edit" menu with Undo (Ctrl+Z, ⌘Z on macOS) and Redo (Ctrl+Y, ⇧⌘Z on macOS); the texts name the setting.
     */
    public JMenu menu() {
        JMenu edit = I18n.text(new JMenu(), "edit.menu");
        JMenuItem u = new JMenuItem(), r = new JMenuItem();
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        u.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask));
        r.setAccelerator(Platform.IS_MAC ? KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask | InputEvent.SHIFT_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_Y, mask));
        u.addActionListener(e -> undo());
        r.addActionListener(e -> redo());
        Runnable update = () -> {
            String un = undo.isEmpty() ? "" : undoName(), rn = redo.isEmpty() ? "" : redoName();
            u.setText(un.isEmpty() ? I18n.tr("edit.undo") : I18n.tr("edit.undoWhat", un));
            r.setText(rn.isEmpty() ? I18n.tr("edit.redo") : I18n.tr("edit.redoWhat", rn));
            u.setEnabled(!undo.isEmpty() || pause.isRunning());
            r.setEnabled(!redo.isEmpty());
        };
        // a pending change (e.g. a slider just released) is recorded when the menu opens
        edit.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) { commit(); update.run(); }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
        });
        addListener(update);
        I18n.bind(update);
        edit.add(u);
        edit.add(r);
        return edit;
    }
}
