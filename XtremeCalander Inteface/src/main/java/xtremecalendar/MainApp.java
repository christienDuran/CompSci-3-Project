package xtremecalendar;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        URL layout = MainApp.class.getResource("/xtremecalendar/main.fxml");
        if (layout == null) {
            throw new IllegalStateException("Could not find /xtremecalendar/main.fxml");
        }

        Parent root = FXMLLoader.load(layout);
        Scene scene = new Scene(root, 1000, 700);

        stage.setTitle("Extreme Calendar");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
