package de.c64gfx.swing;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

/** Project file: plain "key=value" lines (UTF-8, sorted), readable and diff-friendly. */
public final class ProjectFile {
    private final Map<String, String> values = new TreeMap<>();

    public void put(String key, Object value) {
        if (value == null) values.remove(key);
        else values.put(key, value instanceof Enum<?> e ? e.name() : String.valueOf(value));
    }

    public boolean has(String key) { return values.containsKey(key); }

    public String get(String key, String def) { return values.getOrDefault(key, def); }

    public int getInt(String key, int def) {
        try {
            return values.containsKey(key) ? Integer.parseInt(values.get(key).trim()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        return values.containsKey(key) ? Boolean.parseBoolean(values.get(key).trim()) : def;
    }

    public <E extends Enum<E>> E getEnum(String key, Class<E> type, E def) {
        try {
            return values.containsKey(key) ? Enum.valueOf(type, values.get(key).trim()) : def;
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public void save(File file) throws IOException {
        java.util.Properties p = new java.util.Properties() {
            // sorted output
            @Override public synchronized java.util.Set<Map.Entry<Object, Object>> entrySet() {
                java.util.Set<Map.Entry<Object, Object>> s = new java.util.TreeSet<>((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()));
                s.addAll(super.entrySet());
                return s;
            }
        };
        p.putAll(values);
        try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            p.store(w, AppConfig.current().name() + " project");
        }
    }

    public static ProjectFile load(File file) throws IOException {
        java.util.Properties p = new java.util.Properties();
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        ProjectFile f = new ProjectFile();
        for (String k : p.stringPropertyNames()) f.values.put(k, p.getProperty(k));
        return f;
    }
}
