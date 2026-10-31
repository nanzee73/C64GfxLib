package de.c64gfx.swing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTest {

    @Test
    void projectFileRoundTrip(@TempDir Path dir) throws Exception {
        ProjectFile p = new ProjectFile();
        p.put("contrast", 20);
        p.put("serpentine", true);
        p.put("name", "Säge = Ö:ü");
        p.put("mode", Thread.State.RUNNABLE);
        File f = dir.resolve("x.flipperj").toFile();
        p.save(f);
        ProjectFile q = ProjectFile.load(f);
        assertEquals(20, q.getInt("contrast", 0));
        assertTrue(q.getBool("serpentine", false));
        assertEquals("Säge = Ö:ü", q.get("name", ""));
        assertEquals(Thread.State.RUNNABLE, q.getEnum("mode", Thread.State.class, Thread.State.NEW));
        assertEquals(7, q.getInt("missing", 7));
    }

    @Test
    void safeFolderNames() {
        assertEquals("sunset", ProjectFolders.safeName("sunset.png"));
        assertEquals("a_b_c", ProjectFolders.safeName("a:b?c.jpg"));
        assertEquals("project", ProjectFolders.safeName(""));
    }

    @Test
    void anExistingFolderOfAnotherLanguageIsReused(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve("Projekte"));
        File f = ProjectFolders.existingForTest(dir.toFile(), ProjectFolders.PROJECTS, "Projects");
        assertEquals("Projekte", f.getName());
        File g = ProjectFolders.existingForTest(dir.toFile(), ProjectFolders.EXPORT, "export");
        assertEquals("export", g.getName(), "nothing there yet: the name of the current language");
    }
}
