module ru.mediaplayer {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires com.fasterxml.jackson.databind;

    opens ru.mediaplayer to javafx.fxml;
    opens ru.mediaplayer.controller to javafx.fxml;
    exports ru.mediaplayer;
}
