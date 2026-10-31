package de.c64gfx.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The project of the main window: File menu (New, Open picture, Open project, Open recent, Save, Save as, Close,
 * Open project folder, Exit), the unsaved marker in the title and the question before unsaved changes are lost.
 * <p>
 * "Unsaved" means: a picture is loaded and the project was never saved, or the settings differ from the saved ones
 * (compared with the {@link UndoHistory} state).
 */
public final class ProjectSession {
    /** What the application contributes. */
    public interface Handler {
        boolean hasPicture();

        /** File name of the loaded picture, used for the project folder. */
        String pictureName();

        /** Puts the settings into the project file, copies the picture to Import/, writes the results to Export/. */
        void write(ProjectFile project, File projectDir) throws IOException;

        /** Loads the picture of the project and its settings. */
        void read(ProjectFile project, File projectDir) throws IOException;

        /** New / Close: no picture, default settings. */
        void clear();
    }

    private static final int RECENT_MAX = 10;
    private static final String RECENT_KEY = "recentProjects";

    private final JFrame frame;
    private final String extension;
    private final UndoHistory history;
    private final Handler handler;
    private final List<Runnable> listeners = new ArrayList<>();
    private Consumer<String> status = s -> { };
    private File projectFile;
    private Map<String, Object> savedState;

    /** @param extension of project files without dot, e.g. "flipperj" */
    public ProjectSession(JFrame frame, String extension, UndoHistory history, Handler handler) {
        this.frame = frame;
        this.extension = extension;
        this.history = history;
        this.handler = handler;
        history.addListener(this::fire);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { quit(); }
        });
    }

    /** Short messages for the status line ("Project saved: …"). */
    public void setStatus(Consumer<String> status) { this.status = status; }

    /** Called when the title or the menu should be updated. */
    public void addListener(Runnable r) { listeners.add(r); }

    private void fire() { for (Runnable r : listeners) r.run(); }

    public File projectFile() { return projectFile; }

    public File projectDir() { return projectFile == null ? null : projectFile.getParentFile(); }

    /** Project name for the title, e.g. "sunset", "Untitled", or "" without picture. */
    public String name() {
        if (projectFile != null) return projectFile.getName().replaceFirst("\\.[^.]+$", "");
        return handler.hasPicture() ? I18n.tr("project.untitled") : "";
    }

    public boolean isDirty() {
        if (!handler.hasPicture()) return false;
        return projectFile == null || savedState == null || !Objects.equals(savedState, history.state());
    }

    /** Appended to the window title: " – name" plus " •" while unsaved. */
    public String titleSuffix() {
        String n = name();
        return n.isEmpty() ? "" : " – " + n + (isDirty() ? " •" : "");
    }

    /** After a new picture was loaded: it starts a new, unsaved project. */
    public void pictureLoaded() {
        projectFile = null;
        savedState = null;
        fire();
    }

    // ------------------------------------------------------------------------------------------------ actions

    public void newProject() {
        if (!confirmDiscard()) return;
        handler.clear();
        projectFile = null;
        history.reset();
        savedState = history.state();
        fire();
    }

    /** Saves to the project's folder; a new project gets Projekte/&lt;picture name&gt;. */
    public boolean save() {
        if (!handler.hasPicture()) return false;
        File dir = projectDir() != null ? projectDir() : ProjectFolders.newProjectDir(handler.pictureName());
        return saveTo(dir);
    }

    public boolean saveAs() {
        if (!handler.hasPicture()) return false;
        String suggested = ProjectFolders.safeName(projectFile != null ? name() : handler.pictureName());
        File f = FileDialogs.save(frame, "project.saveAs", ProjectFolders.projects(), suggested + "." + extension,
                "project.filter", extension);
        if (f == null) return false;
        File dir = new File(f.getParentFile(), ProjectFolders.safeName(f.getName()));
        return saveTo(dir);
    }

    private boolean saveTo(File dir) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(dir + ": folder cannot be created.");
            ProjectFile p = new ProjectFile();
            p.put("app", AppConfig.current().name());
            handler.write(p, dir);
            File f = new File(dir, dir.getName() + "." + extension);
            p.save(f);
            projectFile = f;
            savedState = history.state();
            addRecent(f);
            status.accept(I18n.tr("project.saved", f.getPath()));
            fire();
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, I18n.tr("project.error.write", e.getMessage()),
                    AppConfig.current().name(), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public void open() {
        if (!confirmDiscard()) return;
        File f = FileDialogs.open(frame, "project.open", ProjectFolders.projects(), "project.filter", extension);
        if (f != null) load(f);
    }

    /** Opens a project file (after asking about unsaved changes). */
    public void open(File f) {
        if (confirmDiscard()) load(f);
    }

    private void load(File f) {
        try {
            ProjectFile p = ProjectFile.load(f);
            handler.read(p, f.getParentFile());
            projectFile = f;
            history.reset();
            savedState = history.state();
            addRecent(f);
            fire();
        } catch (Exception e) {
            removeRecent(f);
            JOptionPane.showMessageDialog(frame, I18n.tr("project.error.read", e.getMessage()),
                    AppConfig.current().name(), JOptionPane.ERROR_MESSAGE);
        }
    }

    public void close() { newProject(); }

    public void showFolder() {
        File dir = projectDir() != null ? projectDir() : ProjectFolders.projects();
        try {
            Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, I18n.tr("project.error.folder", dir.getPath()),
                    AppConfig.current().name(), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Asks before unsaved changes are lost; true if the action may go on. */
    public boolean confirmDiscard() {
        history.commit();
        if (!isDirty()) return true;
        Object[] options = {I18n.tr("project.unsaved.save"), I18n.tr("project.unsaved.discard"), I18n.tr("cancel")};
        int answer = JOptionPane.showOptionDialog(frame, I18n.tr("project.unsaved.text", name()),
                I18n.tr("project.unsaved.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        if (answer == 0) return save();
        return answer == 1;
    }

    /** Close the window / quit the program, after asking about unsaved changes. */
    public boolean quit() {
        if (!confirmDiscard()) return false;
        frame.dispose();
        System.exit(0);
        return true;
    }

    // ------------------------------------------------------------------------------------------------ recent

    public List<File> recent() {
        List<File> list = new ArrayList<>();
        String s = AppConfig.prefs().get(RECENT_KEY, "");
        for (String line : s.split("\n")) if (!line.isBlank()) list.add(new File(line));
        return list;
    }

    private void storeRecent(List<File> list) {
        StringBuilder sb = new StringBuilder();
        for (File f : list) sb.append(f.getAbsolutePath()).append('\n');
        AppConfig.prefs().put(RECENT_KEY, sb.toString());
    }

    private void addRecent(File f) {
        List<File> list = recent();
        list.removeIf(x -> x.getAbsoluteFile().equals(f.getAbsoluteFile()));
        list.add(0, f.getAbsoluteFile());
        while (list.size() > RECENT_MAX) list.remove(list.size() - 1);
        storeRecent(list);
    }

    private void removeRecent(File f) {
        List<File> list = recent();
        list.removeIf(x -> x.getAbsoluteFile().equals(f.getAbsoluteFile()));
        storeRecent(list);
    }

    // ------------------------------------------------------------------------------------------------ menu

    /**
     * The File menu: New, the application's open item, Open project, Open recent, Save, Save as, Close, Open project
     * folder, then the application's own items, Settings and Exit (on macOS both live in the application menu).
     *
     * @param openPicture the application's "Open picture" item
     * @param own         further items of the application (a null entry is a separator)
     * @param settings    "Settings…" item or null
     */
    public JMenu fileMenu(JMenuItem openPicture, List<JMenuItem> own, JMenuItem settings) {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenu file = I18n.text(new JMenu(), "menu.file");
        JMenuItem neu = item("project.new", KeyStroke.getKeyStroke(KeyEvent.VK_N, mask), this::newProject);
        JMenuItem open = item("project.open",
                KeyStroke.getKeyStroke(KeyEvent.VK_O, mask | InputEvent.SHIFT_DOWN_MASK), this::open);
        JMenu recent = I18n.text(new JMenu(), "project.recent");
        JMenuItem save = item("project.save", KeyStroke.getKeyStroke(KeyEvent.VK_S, mask), this::save);
        JMenuItem saveAs = item("project.saveAs",
                KeyStroke.getKeyStroke(KeyEvent.VK_S, mask | InputEvent.SHIFT_DOWN_MASK), this::saveAs);
        JMenuItem close = item("project.close", KeyStroke.getKeyStroke(KeyEvent.VK_W, mask), this::close);
        JMenuItem folder = item("project.showFolder", null, this::showFolder);
        file.add(neu);
        if (openPicture != null) {
            openPicture.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, mask));
            file.add(openPicture);
        }
        file.add(open);
        file.add(recent);
        file.addSeparator();
        file.add(save);
        file.add(saveAs);
        file.add(close);
        file.add(folder);
        if (own != null && !own.isEmpty()) {
            file.addSeparator();
            for (JMenuItem i : own) {
                if (i == null) file.addSeparator();
                else file.add(i);
            }
        }
        if (!Platform.IS_MAC) {
            if (settings != null) {
                file.addSeparator();
                file.add(settings);
            }
            file.addSeparator();
            file.add(item("file.exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, mask), this::quit));
        }
        file.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                history.commit();
                boolean pic = handler.hasPicture();
                save.setEnabled(pic);
                saveAs.setEnabled(pic);
                close.setEnabled(pic || projectFile != null);
                fillRecent(recent);
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
        });
        fillRecent(recent);
        return file;
    }

    private void fillRecent(JMenu recent) {
        recent.removeAll();
        List<File> list = recent();
        for (File f : list) {
            JMenuItem i = new JMenuItem(f.getName().replaceFirst("\\.[^.]+$", "") + "  –  " + f.getParent());
            i.setEnabled(f.isFile());
            i.addActionListener(e -> open(f));
            recent.add(i);
        }
        if (list.isEmpty()) {
            JMenuItem none = I18n.text(new JMenuItem(), "project.recentNone");
            none.setEnabled(false);
            recent.add(none);
        } else {
            recent.addSeparator();
            recent.add(item("project.recentClear", null, () -> storeRecent(List.of())));
        }
    }

    private static JMenuItem item(String key, KeyStroke key2, Runnable action) {
        JMenuItem i = I18n.text(new JMenuItem(), key);
        if (key2 != null) i.setAccelerator(key2);
        i.addActionListener(e -> action.run());
        return i;
    }
}
