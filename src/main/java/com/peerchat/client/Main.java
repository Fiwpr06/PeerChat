package com.peerchat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

// Điểm khởi chạy ứng dụng giao diện JavaFX PeerChat
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/connect.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 720, 540);

        primaryStage.setTitle("PEERCHAT // YORHA SYSTEM TERMINAL");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(640);
        primaryStage.setMinHeight(480);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
