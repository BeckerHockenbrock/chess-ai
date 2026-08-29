package com.becker;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Chess extends Application {

    private static Scene scene;
    private ChessGui controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(Chess.class.getResource("ChessGui.fxml"));
        scene = new Scene(loader.load());
        controller = loader.getController();
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> controller.closeEngines());
        stage.sizeToScene();
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Chess.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }

}
