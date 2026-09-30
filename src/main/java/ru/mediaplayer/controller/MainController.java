package ru.mediaplayer.controller;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioEqualizer;
import javafx.scene.media.EqualizerBand;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import ru.mediaplayer.model.Track;
import ru.mediaplayer.service.AppSettings;
import ru.mediaplayer.service.LibraryStore;
import ru.mediaplayer.service.MediaService;
import ru.mediaplayer.util.FormatUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {
    @FXML
    private ListView<Track> trackList;
    @FXML
    private ListView<String> playlistList;
    @FXML
    private Canvas visualizerCanvas, backgroundCanvas, waveformCanvas;
    @FXML
    private MediaView mediaView;
    @FXML
    private Label pageTitle, pageSubtitle, titleLabel, artistLabel, currentTimeLabel, durationLabel, statusLabel, trackCountLabel;
    @FXML
    private Slider volumeSlider;
    @FXML
    private Button playButton, shuffleButton, repeatButton;
    @FXML
    private ToggleButton equalizerToggle;
    @FXML
    private ImageView coverView;
    @FXML
    private VBox equalizerBox, playlistPane, libraryPane;
    @FXML
    private Slider band60, band170, band310, band600, band1000, band3000, band6000, band12000, band14000;

    private final ObservableList<Track> tracks = FXCollections.observableArrayList();
    private final ObservableList<Track> recent = FXCollections.observableArrayList();
    private final ObservableList<String> playlistNames = FXCollections.observableArrayList();
    private final Map<String, ObservableList<Track>> playlists = new LinkedHashMap<>();
    private final MediaService mediaService = new MediaService();
    private final LibraryStore store = new LibraryStore();
    private final AppSettings settings = new AppSettings();
    private final Map<String, String> coverPaths = new LinkedHashMap<>();
    private final float[] spectrum = new float[64];
    private final List<Double> waveform = new ArrayList<>();
    private AnimationTimer animationTimer;
    private AudioEqualizer equalizer;
    private Track currentTrack;
    private String activePlaylist;
    private boolean seeking, shuffle, repeat;
    private int visualizerMode;
    private Stage fullscreenStage;
    private Canvas fullscreenCanvas;

    @FXML
    private void initialize() {
        trackList.setItems(tracks);
        playlistList.setItems(playlistNames);
        trackList.setPlaceholder(new Label("Перетащи сюда файлы или нажми «Добавить»"));
        playlistList.setPlaceholder(new Label("Создай первый плейлист"));
        trackList.setCellFactory(list -> new ListCell<>() {
            private final ImageView cover = new ImageView();
            private final javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(10);
            private final VBox labels = new VBox(2);
            private final Label title = new Label(), artist = new Label();

            {
                cover.setFitWidth(38);
                cover.setFitHeight(38);
                cover.setPreserveRatio(false);
                cover.setClip(new Rectangle(38, 38));
                title.getStyleClass().add("track-cell-title");
                artist.getStyleClass().add("track-cell-artist");
                labels.getChildren().addAll(title, artist);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.getChildren().addAll(cover, labels);
            }

            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    title.setText(item.title());
                    artist.setText(item.artist());
                    cover.setImage(item.cover() == null ? iconImage("cover-placeholder.png") : item.cover());
                    setGraphic(row);
                    setText(null);
                }
            }
        });
        trackList.getSelectionModel().selectedItemProperty().addListener((o, old, item) -> {
            if (item != null && !item.equals(currentTrack)) playTrack(item);
        });
        playlistList.getSelectionModel().selectedItemProperty().addListener((o, old, name) -> {
            if (name == null) return;
            activePlaylist = name;
            pageTitle.setText(name);
            pageSubtitle.setText("Плейлист · " + playlists.get(name).size() + " треков");
            trackList.setItems(playlists.get(name));
            libraryPane.setVisible(true);
            libraryPane.setManaged(true);
            playlistPane.setVisible(false);
            playlistPane.setManaged(false);
        });
        volumeSlider.setValue(parseDouble(settings.get("volume", "70"), 70, 0, 100));
        volumeSlider.valueProperty().addListener((o, old, v) -> {
            if (mediaService.player() != null) mediaService.player().setVolume(v.doubleValue() / 100);
            settings.set("volume", Double.toString(v.doubleValue()));
        });
        waveformCanvas.widthProperty().addListener(o -> drawWaveform());
        waveformCanvas.heightProperty().addListener(o -> drawWaveform());
        waveformCanvas.setOnMouseClicked(e -> {
            if (mediaService.player() != null && waveformCanvas.getWidth() > 0)
                mediaService.seek(e.getX() / waveformCanvas.getWidth() * mediaService.player().getTotalDuration().toSeconds());
        });
        equalizerBox.setDisable(!equalizerToggle.isSelected());
        equalizerToggle.selectedProperty().addListener((o, a, b) -> {
            equalizerBox.setDisable(!b);
            if (equalizer != null) equalizer.setEnabled(b);
        });
        wireEqualizer();
        loadLibrary();
        installContextMenu();
        installDragDrop();
        installHotkeys();
        visualizerMode = parseInt(settings.get("visualizerMode", "0"), 0, 3, 0);
        shuffle = Boolean.parseBoolean(settings.get("shuffle", "false"));
        repeat = Boolean.parseBoolean(settings.get("repeat", "false"));
        updateModeButtons();
        startAnimations();
        updateCount();
    }

    private int parseInt(String value, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback, double min, double max) {
        try {
            return Math.max(min, Math.min(max, Double.parseDouble(value)));
        } catch (Exception e) {
            return fallback;
        }
    }

    private Image iconImage(String name) {
        try {
            return new Image(getClass().getResourceAsStream("/ru/mediaplayer/images/icons/" + name));
        } catch (Exception e) {
            return null;
        }
    }

    private ImageView icon(String name, int size) {
        Image image = iconImage(name);
        if (image == null) return null;
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        return view;
    }

    private void loadLibrary() {
        LibraryStore.Data data = store.load();
        coverPaths.putAll(data.covers());
        for (String p : data.tracks()) {
            addTrack(Path.of(p), false);
            findTrack(p).ifPresent(t -> {
                String cp = coverPaths.get(p);
                if (cp != null && Files.isRegularFile(Path.of(cp)))
                    t.setCover(new Image(Path.of(cp).toUri().toString()));
            });
        }
        for (var entry : data.playlists().entrySet()) {
            ObservableList<Track> list = FXCollections.observableArrayList();
            for (String path : entry.getValue()) findTrack(path).ifPresent(list::add);
            playlists.put(entry.getKey(), list);
            playlistNames.add(entry.getKey());
        }
        for (String path : data.recent()) findTrack(path).ifPresent(recent::add);
    }

    private Optional<Track> findTrack(String path) {
        return tracks.stream().filter(t -> t.path().toString().equals(path)).findFirst();
    }

    private void persist() {
        Map<String, List<String>> saved = new LinkedHashMap<>();
        playlists.forEach((name, list) -> saved.put(name, list.stream().map(t -> t.path().toString()).toList()));
        store.save(new LibraryStore.Data(tracks.stream().map(t -> t.path().toString()).toList(), saved, recent.stream().map(t -> t.path().toString()).toList(), coverPaths));
    }

    private void addTrack(Path path, boolean select) {
        if (!Files.isRegularFile(path) || tracks.stream().anyMatch(t -> t.path().equals(path))) return;
        Track track = new Track(path.getFileName().toString(), path);
        tracks.add(track);
        if (select) {
            showLibrary();
            trackList.getSelectionModel().select(track);
        }
    }

    @FXML
    private void showLibrary() {
        pageTitle.setText("Медиатека");
        pageSubtitle.setText("Все добавленные аудио и видео");
        activePlaylist = null;
        libraryPane.setVisible(true);
        libraryPane.setManaged(true);
        playlistPane.setVisible(false);
        playlistPane.setManaged(false);
        trackList.setItems(tracks);
    }

    @FXML
    private void showRecent() {
        pageTitle.setText("Недавние");
        pageSubtitle.setText("Последние воспроизведённые треки");
        activePlaylist = null;
        libraryPane.setVisible(true);
        libraryPane.setManaged(true);
        playlistPane.setVisible(false);
        playlistPane.setManaged(false);
        trackList.setItems(recent);
    }

    @FXML
    private void showPlaylists() {
        pageTitle.setText("Плейлисты");
        pageSubtitle.setText("Твои музыкальные подборки");
        libraryPane.setVisible(false);
        libraryPane.setManaged(false);
        playlistPane.setVisible(true);
        playlistPane.setManaged(true);
    }

    @FXML
    private void openFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Добавить медиафайлы");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Медиа", "*.mp3", "*.wav", "*.aiff", "*.aif", "*.m4a", "*.mp4", "*.m4v", "*.fxm", "*.flv", "*.aac", "*.ogg"), new FileChooser.ExtensionFilter("Все файлы", "*.*"));
        List<File> files = chooser.showOpenMultipleDialog(trackList.getScene().getWindow());
        if (files != null) importFiles(files.stream().map(File::toPath).toList());
    }

    private void importFiles(List<Path> paths) {
        int before = tracks.size();
        paths.forEach(p -> addTrack(p.toAbsolutePath().normalize(), false));
        showLibrary();
        updateCount();
        statusLabel.setText("Добавлено файлов: " + (tracks.size() - before));
        if (currentTrack == null && tracks.size() > before) trackList.getSelectionModel().select(before);
        persist();
    }

    @FXML
    private void createPlaylist() {
        TextInputDialog d = new TextInputDialog("Новый плейлист");
        d.setTitle("Плейлист");
        d.setHeaderText("Создать подборку");
        d.setContentText("Название:");
        d.showAndWait().map(String::trim).filter(s -> !s.isBlank()).ifPresent(name -> {
            if (playlists.containsKey(name)) {
                statusLabel.setText("Плейлист уже существует");
                return;
            }
            playlists.put(name, FXCollections.observableArrayList());
            playlistNames.add(name);
            showPlaylists();
            playlistList.getSelectionModel().select(name);
            persist();
        });
    }

    @FXML
    private void deletePlaylist() {
        String name = playlistList.getSelectionModel().getSelectedItem();
        if (name != null) {
            playlists.remove(name);
            playlistNames.remove(name);
            persist();
            showPlaylists();
        }
    }

    @FXML
    private void addToPlaylist() {
        Track track = trackList.getSelectionModel().getSelectedItem();
        if (track == null) return;
        if (playlistNames.isEmpty()) {
            createPlaylist();
            return;
        }
        ChoiceDialog<String> d = new ChoiceDialog<>(playlistNames.get(0), playlistNames);
        d.setTitle("Добавить в плейлист");
        d.setHeaderText(track.title());
        d.setContentText("Плейлист:");
        d.showAndWait().ifPresent(n -> {
            if (!playlists.get(n).contains(track)) playlists.get(n).add(track);
            persist();
            statusLabel.setText("Добавлено в «" + n + "»");
        });
    }

    @FXML
    private void playPause() {
        MediaPlayer p = mediaService.player();
        if (p == null) {
            Track t = trackList.getSelectionModel().getSelectedItem();
            if (t != null) playTrack(t);
            else openFiles();
        } else if (p.getStatus() == MediaPlayer.Status.PLAYING) p.pause();
        else p.play();
    }

    @FXML
    private void previousTrack() {
        switchTrack(-1);
    }

    @FXML
    private void nextTrack() {
        switchTrack(1);
    }

    private ObservableList<Track> activeTracks() {
        ObservableList<Track> list = trackList.getItems();
        return list == null || list.isEmpty() ? tracks : list;
    }

    private void switchTrack(int direction) {
        ObservableList<Track> list = activeTracks();
        if (list.isEmpty()) return;
        int index = list.indexOf(currentTrack);
        if (shuffle && list.size() > 1) {
            List<Track> options = list.stream().filter(t -> !t.equals(currentTrack)).toList();
            playTrack(options.get(new Random().nextInt(options.size())));
            return;
        }
        Track next = list.get(Math.floorMod(index + direction, list.size()));
        trackList.getSelectionModel().select(next);
        if (next.equals(currentTrack)) playTrack(next);
    }

    @FXML
    private void stop() {
        if (mediaService.player() != null) {
            mediaService.player().stop();
            playButton.setGraphic(icon("play.png", 22));
            statusLabel.setText("Остановлено");
        }
    }

    @FXML
    private void toggleShuffle() {
        shuffle = !shuffle;
        settings.set("shuffle", Boolean.toString(shuffle));
        updateModeButtons();
    }

    @FXML
    private void toggleRepeat() {
        repeat = !repeat;
        settings.set("repeat", Boolean.toString(repeat));
        updateModeButtons();
    }

    private void updateModeButtons() {
        shuffleButton.setOpacity(shuffle ? 1 : .55);
        repeatButton.setOpacity(repeat ? 1 : .55);
    }

    @FXML
    private void removeTrack() {
        Track t = trackList.getSelectionModel().getSelectedItem();
        if (t == null) return;
        if (activePlaylist != null && playlists.containsKey(activePlaylist)) playlists.get(activePlaylist).remove(t);
        else {
            tracks.remove(t);
            recent.remove(t);
            playlists.values().forEach(l -> l.remove(t));
        }
        if (t.equals(currentTrack)) {
            mediaService.close();
            currentTrack = null;
            mediaView.setMediaPlayer(null);
            coverView.setImage(iconImage("cover-placeholder.png"));
            titleLabel.setText("Ничего не воспроизводится");
        }
        updateCount();
        persist();
    }

    private void playTrack(Track track) {
        try {
            currentTrack = track;
            waveform.clear();
            MediaPlayer player = mediaService.open(track.path());
            mediaView.setMediaPlayer(player);
            player.setVolume(volumeSlider.getValue() / 100);
            player.setAudioSpectrumNumBands(spectrum.length);
            player.setAudioSpectrumInterval(.04);
            player.setAudioSpectrumThreshold(-60);
            player.setAudioSpectrumListener((ts, d, m, p) -> {
                System.arraycopy(m, 0, spectrum, 0, Math.min(m.length, spectrum.length));
                if (waveform.size() < 900)
                    waveform.add(m.length == 0 ? 0 : Arrays.stream(toDouble(m)).average().orElse(0));
            });
            player.setOnReady(() -> {
                Map<String, Object> meta = player.getMedia().getMetadata();
                Object title = meta.get("title"), artist = meta.get("artist"), album = meta.get("album"), image = meta.get("image");
                if (title instanceof String s) track.setTitle(s);
                if (artist instanceof String s) track.setArtist(s);
                if (album instanceof String s) track.setAlbum(s);
                if (image instanceof Image i) track.setCover(i);
                titleLabel.setText(track.title());
                artistLabel.setText(track.artist());
                coverView.setImage(track.cover() == null ? iconImage("cover-placeholder.png") : track.cover());
                durationLabel.setText(FormatUtils.time(player.getTotalDuration().toSeconds()));
                equalizer = player.getAudioEqualizer();
                equalizer.setEnabled(equalizerToggle.isSelected());
                applyEqualizerValues();
                boolean video = player.getMedia().getWidth() > 0 || player.getMedia().getHeight() > 0;
                mediaView.setVisible(video);
                visualizerCanvas.setVisible(!video);
                player.play();
                playButton.setGraphic(icon("pause.png", 22));
                statusLabel.setText("Воспроизведение");
                recent.remove(track);
                recent.add(0, track);
                while (recent.size() > 30) recent.remove(recent.size() - 1);
                trackList.refresh();
                persist();
            });
            player.currentTimeProperty().addListener((o, old, now) -> {
                currentTimeLabel.setText(FormatUtils.time(now.toSeconds()));
                drawWaveform();
            });
            player.setOnEndOfMedia(() -> {
                if (repeat) player.seek(Duration.ZERO);
                else nextTrack();
            });
            player.setOnPaused(() -> playButton.setGraphic(icon("play.png", 22)));
            player.setOnPlaying(() -> playButton.setGraphic(icon("pause.png", 22)));
            player.setOnError(() -> statusLabel.setText("Ошибка воспроизведения"));
        } catch (RuntimeException e) {
            statusLabel.setText("Не удалось открыть файл");
            showError(e.getMessage());
        }
    }

    private double[] toDouble(float[] a) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i];
        return r;
    }

    private void drawWaveform() {
        if (waveformCanvas == null) return;
        double w = waveformCanvas.getWidth(), h = waveformCanvas.getHeight();
        GraphicsContext g = waveformCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        if (w <= 0 || h <= 0) return;
        double duration = mediaService.player() == null ? 0 : mediaService.player().getTotalDuration().toSeconds();
        double progress = duration > 0 ? mediaService.player().getCurrentTime().toSeconds() / duration : 0;
        int n = Math.max(1, (int) w / 3);
        for (int i = 0; i < n; i++) {
            double v = waveform.isEmpty() ? .08 : waveform.get(Math.min(waveform.size() - 1, i * waveform.size() / n));
            double amp = Math.max(.08, Math.min(1, (v + 60) / 60));
            double bh = amp * h * .85;
            double x = i * 3;
            g.setFill(i / (double) n <= progress ? Color.web("#c35cff") : Color.web("#485078"));
            g.fillRoundRect(x, (h - bh) / 2, 2, bh, 2, 2);
        }
    }

    private void wireEqualizer() {
        eqSliders().forEach(s -> s.valueProperty().addListener((o, a, b) -> applyEqualizerValues()));
    }

    private List<Slider> eqSliders() {
        return List.of(band60, band170, band310, band600, band1000, band3000, band6000, band12000, band14000);
    }

    private void applyEqualizerValues() {
        if (equalizer == null) return;
        List<EqualizerBand> b = equalizer.getBands();
        for (int i = 0; i < Math.min(b.size(), eqSliders().size()); i++)
            b.get(i).setGain(eqSliders().get(i).getValue());
    }

    @FXML
    private void resetEqualizer() {
        eqSliders().forEach(s -> s.setValue(0));
    }

    @FXML
    private void nextVisualizer() {
        visualizerMode = (visualizerMode + 1) % 4;
        settings.set("visualizerMode", Integer.toString(visualizerMode));
        statusLabel.setText("Визуализатор: " + new String[]{"Столбцы", "Зеркало", "Волна", "Кольцо"}[visualizerMode]);
    }

    @FXML
    private void fullscreenVisualizer() {
        if (fullscreenStage != null) {
            fullscreenStage.close();
            fullscreenStage = null;
            return;
        }
        fullscreenStage = new Stage(StageStyle.UNDECORATED);
        fullscreenCanvas = new Canvas(1200, 800);
        StackPane root = new StackPane(fullscreenCanvas);
        root.setStyle("-fx-background-color: #070b16;");
        Scene scene = new Scene(root, 1200, 800, Color.BLACK);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.F) {
                fullscreenStage.close();
                fullscreenStage = null;
            }
        });
        root.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                fullscreenStage.close();
                fullscreenStage = null;
            }
        });
        fullscreenCanvas.widthProperty().bind(scene.widthProperty());
        fullscreenCanvas.heightProperty().bind(scene.heightProperty());
        fullscreenStage.setScene(scene);
        fullscreenStage.setFullScreen(true);
        fullscreenStage.show();
    }

    @FXML
    private void openSettings() {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Настройки Auralis");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ComboBox<String> modes = new ComboBox<>(FXCollections.observableArrayList("Столбцы", "Зеркало", "Волна", "Кольцо"));
        modes.getSelectionModel().select(visualizerMode);
        CheckBox save = new CheckBox("Сохранять библиотеку и плейлисты");
        save.setSelected(true);
        VBox box = new VBox(12, new Label("Режим визуализатора"), modes, save, new Label("Данные: " + LibraryStore.dataDirectory()));
        d.getDialogPane().setContent(box);
        d.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            visualizerMode = modes.getSelectionModel().getSelectedIndex();
            settings.set("visualizerMode", Integer.toString(visualizerMode));
        });
    }

    private void installHotkeys() {
        trackList.sceneProperty().addListener((o, old, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.SPACE), this::playPause);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.CONTROL_DOWN), this::nextTrack);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.CONTROL_DOWN), this::previousTrack);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), this::openFiles);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), this::fullscreenVisualizer);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN), this::openSettings);
        });
    }

    private void installDragDrop() {
        trackList.setOnDragOver(e -> {
            if (e.getGestureSource() != trackList && e.getDragboard().hasFiles())
                e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        trackList.setOnDragDropped(e -> {
            var files = e.getDragboard().getFiles();
            if (files != null) importFiles(files.stream().map(File::toPath).toList());
            e.setDropCompleted(files != null && !files.isEmpty());
            e.consume();
        });
    }

    private void installContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem play = new MenuItem("Воспроизвести", icon("play.png", 16));
        play.setOnAction(e -> {
            Track t = trackList.getSelectionModel().getSelectedItem();
            if (t != null) playTrack(t);
        });
        MenuItem add = new MenuItem("Добавить в плейлист", icon("playlist-add.png", 16));
        add.setOnAction(e -> addToPlaylist());
        MenuItem remove = new MenuItem("Удалить из списка", icon("delete.png", 16));
        remove.setOnAction(e -> removeTrack());
        MenuItem cover = new MenuItem("Выбрать обложку…");
        cover.setOnAction(e -> chooseCover());
        menu.getItems().addAll(play, add, cover, new SeparatorMenuItem(), remove);
        trackList.setContextMenu(menu);
    }

    private void chooseCover() {
        Track t = trackList.getSelectionModel().getSelectedItem();
        if (t == null) return;
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File f = fc.showOpenDialog(trackList.getScene().getWindow());
        if (f != null) {
            try {
                Path dir = LibraryStore.dataDirectory().resolve("covers");
                Files.createDirectories(dir);
                Path target = dir.resolve(Integer.toHexString(t.path().toString().hashCode()) + "-" + f.getName());
                Files.copy(f.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                t.setCoverPath(target.toString());
                coverPaths.put(t.path().toString(), target.toString());
                t.setCover(new Image(target.toUri().toString()));
                coverView.setImage(t.cover());
                trackList.refresh();
                persist();
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        }
    }

    private void updateCount() {
        trackCountLabel.setText(tracks.size() + " файлов");
    }

    private void startAnimations() {
        animationTimer = new AnimationTimer() {
            public void handle(long now) {
                double t = now / 1e9;
                drawBackground(t);
                drawVisualizer(t);
                if (fullscreenCanvas != null) drawOn(fullscreenCanvas, t);
                if (coverView != null && mediaService.player() != null && mediaService.player().getStatus() == MediaPlayer.Status.PLAYING) {
                    double pulse = 1 + Math.max(0, spectrum[0] + 60) / 60.0 * .035;
                    coverView.setScaleX(pulse);
                    coverView.setScaleY(pulse);
                } else if (coverView != null) {
                    coverView.setScaleX(1);
                    coverView.setScaleY(1);
                }
            }
        };
        animationTimer.start();
    }

    private void drawBackground(double t) {
        double w = backgroundCanvas.getWidth(), h = backgroundCanvas.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = backgroundCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setFill(Color.web("#070b16"));
        g.fillRect(0, 0, w, h);
        double energy = musicEnergy();
        for (int i = 0; i < 7; i++) {
            double x = w * (.05 + i * .16) + Math.sin(t * (.13 + i * .015) + i) * (50 + energy * 100), y = h * (.15 + (i % 4) * .24) + Math.cos(t * .17 + i) * (40 + energy * 80), r = 100 + energy * 150 + Math.sin(t * .5 + i) * 20;
            g.setFill(Color.hsb(245 + i * 16, .72, .72, .035 + energy * .06));
            g.fillOval(x - r, y - r, r * 2, r * 2);
        }
    }

    private void drawVisualizer(double t) {
        if (visualizerCanvas.isVisible()) drawOn(visualizerCanvas, t);
    }

    private void drawOn(Canvas canvas, double t) {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        boolean playing = mediaService.player() != null && mediaService.player().getStatus() == MediaPlayer.Status.PLAYING;
        int n = 56;
        double energy = musicEnergy();
        if (visualizerMode == 3) {
            double radius = Math.min(w, h) * (.16 + energy * .04);
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n, level = Math.max(0, Math.min(1, (spectrum[i] + 60) / 52.0));
                double r = radius + (playing ? level : 0) * Math.min(w, h) * .16;
                double x = w / 2 + Math.cos(a) * r, y = h / 2 + Math.sin(a) * r;
                g.setFill(Color.hsb(250 + i * 1.7, .8, 1, .9));
                g.fillOval(x - 2, y - 2, 4, 4);
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            double level = Math.max(0, Math.min(1, (spectrum[i] + 60) / 52.0)), idle = .025 + (Math.sin(t * 3 + i * .6) + 1) * .018, bh = (playing ? level : idle) * h * .62 + 2, x = i * (w / n), bw = Math.max(2, w / n - 3);
            g.setFill(Color.hsb(245 + i * 1.7, .75, 1, .85));
            if (visualizerMode == 0) g.fillRoundRect(x, h * .83 - bh, bw, bh, bw, bw);
            else if (visualizerMode == 1) {
                g.fillRoundRect(x, h / 2 - bh / 2, bw, bh / 2, bw, bw);
                g.fillRoundRect(x, h / 2, bw, bh / 2, bw, bw);
            } else {
                double y = h / 2 - Math.sin(i * .23 + t * 2) * bh * .5;
                g.fillOval(x, y, bw, Math.max(2, bh * .14));
            }
        }
    }

    private double musicEnergy() {
        double sum = 0;
        for (float d : spectrum) sum += Math.max(0, Math.min(1, (d + 60) / 60));
        return Math.min(1, sum / spectrum.length * 2.2);
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Ошибка воспроизведения");
        a.setHeaderText(null);
        a.setContentText(message == null ? "Не удалось открыть файл" : message);
        a.show();
    }

    public void shutdown() {
        persist();
        if (animationTimer != null) animationTimer.stop();
        mediaService.close();
        if (fullscreenStage != null) fullscreenStage.close();
    }

    @FXML
    private void close() {
        shutdown();
        Platform.exit();
    }
}
