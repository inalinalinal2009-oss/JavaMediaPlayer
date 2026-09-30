package ru.mediaplayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class LibraryStore {
    private static final Path DIR = Path.of(System.getProperty("user.home"), ".auralis");
    private static final Path FILE = DIR.resolve("library.json");
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public record Data(List<String> tracks, Map<String, List<String>> playlists, List<String> recent,
                       Map<String, String> covers) {
        public Data() {
            this(new ArrayList<>(), new LinkedHashMap<>(), new ArrayList<>(), new LinkedHashMap<>());
        }
    }

    public Data load() {
        if (!Files.exists(FILE)) return new Data();
        try {
            Data d = mapper.readValue(FILE.toFile(), Data.class);
            return new Data(d.tracks() == null ? new ArrayList<>() : d.tracks(), d.playlists() == null ? new LinkedHashMap<>() : d.playlists(), d.recent() == null ? new ArrayList<>() : d.recent(), d.covers() == null ? new LinkedHashMap<>() : d.covers());
        } catch (IOException | RuntimeException e) {
            return new Data();
        }
    }

    public void save(Data data) {
        try {
            Files.createDirectories(DIR);
            Path tmp = DIR.resolve("library.tmp");
            mapper.writeValue(tmp.toFile(), data);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.createDirectories(DIR);
                mapper.writeValue(FILE.toFile(), data);
            } catch (IOException ignored) {
            }
        }
    }

    public static Path dataDirectory() {
        return DIR;
    }
}
