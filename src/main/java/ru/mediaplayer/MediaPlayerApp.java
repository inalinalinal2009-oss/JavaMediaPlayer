package ru.mediaplayer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MediaPlayerApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                MediaPlayerApp.class.getResource("/ru/mediaplayer/main-view.fxml")
        );
        Scene scene = new Scene(loader.load(), 1180, 760);
        ru.mediaplayer.controller.MainController controller = loader.getController();
        stage.setOnCloseRequest(event -> controller.shutdown());
        scene.getStylesheets().add(
                MediaPlayerApp.class.getResource("/ru/mediaplayer/styles/main.css").toExternalForm()
        );
        stage.setTitle("Auralis — Media Player");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
