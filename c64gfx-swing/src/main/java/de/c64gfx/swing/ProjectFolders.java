package de.c64gfx.swing;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Where projects live:
 * <pre>
 *   &lt;base&gt;/Projekte/&lt;picture name&gt;/&lt;picture name&gt;.&lt;ext&gt;   project file
 *                                          Import/                original picture
 *                                          Export/                results
 * </pre>
 * The base is the program folder if it can be written (portable versions), otherwise
 * {@code Documents/<App name>}. Folder names follow the language of the program; a projects folder that already exists
 * under another language's name is used instead of creating a second one.
 */
public final class ProjectFolders {
    private ProjectFolders() {}

    /** Names of the folders in all library languages (to find folders made in another language). */
    static final List<String> PROJECTS = List.of("Projects", "Projekte", "Projekty", "Projekt", "Projecten");
    static final List<String> IMPORT = List.of("import", "Import");
    static final List<String> EXPORT = List.of("export", "Export", "eksport");

    /** Folder that holds the projects folder. */
    public static File base() {
        File program = programDir();
        if (program != null && writable(program)) return program;
        File home = new File(System.getProperty("user.home"));
        File docs = new File(home, "Documents");
        return new File(docs.isDirectory() ? docs : home, AppConfig.current().name());
    }

    /** The projects folder (created if missing). */
    public static File projects() {
        File base = base();
        File dir = existing(base, PROJECTS, I18n.tr("project.dir.projects"));
        dir.mkdirs();
        return dir;
    }

    public static File importDir(File projectDir) {
        return existing(projectDir, IMPORT, I18n.tr("project.dir.import"));
    }

    public static File exportDir(File projectDir) {
        return existing(projectDir, EXPORT, I18n.tr("project.dir.export"));
    }

    /** A new, not yet existing folder for a picture: "name", "name (2)", … */
    public static File newProjectDir(String pictureName) {
        String name = safeName(pictureName);
        File parent = projects();
        File dir = new File(parent, name);
        for (int i = 2; dir.exists(); i++) dir = new File(parent, name + " (" + i + ")");
        return dir;
    }

    /** File name without extension, without characters that are not allowed in file names. */
    public static String safeName(String name) {
        String n = name == null ? "" : name.replaceFirst("\\.[^.]+$", "");
        n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return n.isEmpty() ? "project" : n;
    }

    private static File existing(File parent, List<String> names, String preferred) {
        File want = new File(parent, preferred);
        if (want.isDirectory()) return want;
        for (String n : names) {
            File f = new File(parent, n);
            if (f.isDirectory()) return f;
        }
        return want;
    }

    static File existingForTest(File parent, List<String> names, String preferred) {
        return existing(parent, names, preferred);
    }

    private static boolean writable(File dir) {
        try {
            Path probe = Files.createTempFile(dir.toPath(), ".write-test", ".tmp");
            Files.delete(probe);
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    /**
     * Folder of the installed program: the jar's folder without lib/, app/ or lib/app/ (start scripts, jpackage);
     * on macOS the folder that contains the .app; while developing (classes folder) the working directory.
     */
    static File programDir() {
        try {
            var src = ProjectFolders.class.getProtectionDomain().getCodeSource();
            if (src == null) return null;
            File f = new File(src.getLocation().toURI());
            if (f.isDirectory()) return new File(System.getProperty("user.dir"));
            File dir = f.getParentFile();
            String path = dir.getAbsolutePath();
            int app = path.indexOf(".app" + File.separator + "Contents");
            if (app >= 0) return new File(path.substring(0, app + 4)).getParentFile();
            if (dir.getName().equals("app")) dir = dir.getParentFile();          // jpackage: app/ or lib/app/
            if (dir != null && dir.getName().equals("lib")) dir = dir.getParentFile(); // start scripts: lib/
            return dir;
        } catch (URISyntaxException | SecurityException e) {
            return null;
        }
    }
}
