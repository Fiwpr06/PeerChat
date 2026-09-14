package com.peerchat.server;

import com.peerchat.server.controller.ServerController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

// Điểm khởi động giao diện đồ họa quản trị Server
public class ServerApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/server.fxml"));
        Parent root = loader.load();

        ServerController controller = loader.getController();

        Scene scene = new Scene(root, 960, 650);
        stage.setScene(scene);
        stage.setTitle("PEERCHAT // MÁY CHỦ TRUNG TÂM (SERVER COMMAND CENTER)");
        stage.setMinWidth(750);
        stage.setMinHeight(500);

        stage.setOnCloseRequest(e -> {
            controller.cleanup();
            Platform.exit();
            System.exit(0);
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}