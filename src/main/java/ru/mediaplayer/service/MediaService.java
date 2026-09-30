package ru.mediaplayer.service;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.nio.file.Path;

public final class MediaService implements AutoCloseable {
    private MediaPlayer player;

    public MediaPlayer open(Path path) {
        close();
        Media media = new Media(path.toAbsolutePath().toUri().toString());
        player = new MediaPlayer(media);
        return player;
    }

    public MediaPlayer player() {
        return player;
    }

    public void seek(double seconds) {
        if (player != null && Double.isFinite(seconds)) player.seek(Duration.seconds(Math.max(0, seconds)));
    }

    @Override
    public void close() {
        if (player != null) {
            player.stop();
            player.dispose();
            player = null;
        }
    }
}
