package ru.mediaplayer.model;

import javafx.scene.image.Image;

import java.nio.file.Path;

public final class Track {
    private String title;
    private String artist = "Неизвестный исполнитель";
    private String album = "";
    private final Path path;
    private Image cover;
    private String coverPath;

    public Track(String title, Path path) {
        this.title = title == null || title.isBlank() ? (path == null || path.getFileName() == null ? "Без названия" : path.getFileName().toString()) : title;
        this.path = path;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        if (title != null && !title.isBlank()) this.title = title;
    }

    public String artist() {
        return artist;
    }

    public void setArtist(String artist) {
        if (artist != null && !artist.isBlank()) this.artist = artist;
    }

    public String album() {
        return album;
    }

    public void setAlbum(String album) {
        if (album != null) this.album = album;
    }

    public Path path() {
        return path;
    }

    public Image cover() {
        return cover;
    }

    public String coverPath() {
        return coverPath;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }

    public void setCover(Image cover) {
        this.cover = cover;
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Track t && path.equals(t.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
