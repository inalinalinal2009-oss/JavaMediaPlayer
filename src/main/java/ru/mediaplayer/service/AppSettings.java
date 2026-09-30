package ru.mediaplayer.service;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public final class AppSettings {
    private final Path file = LibraryStore.dataDirectory().resolve("settings.properties");
    private final Properties values = new Properties();

    public AppSettings() {
        if (Files.exists(file)) try (var in = Files.newInputStream(file)) {
            values.load(in);
        } catch (IOException ignored) {
        }
    }

    public String get(String key, String fallback) {
        return values.getProperty(key, fallback);
    }

    public void set(String key, String value) {
        values.setProperty(key, value);
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                values.store(out, "Auralis settings");
            }
        } catch (IOException ignored) {
        }
    }
}
