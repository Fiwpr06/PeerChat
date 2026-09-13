package com.peerchat.client.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.util.Optional;

// Tiện ích hiển thị hộp thoại giao diện và điều phối luồng JavaFX
public final class ClientUtils {
    private ClientUtils() {}

    // Đảm bảo tác vụ luôn chạy trên luồng giao diện JavaFX Application Thread
    public static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    // Hiển thị hộp thoại thông báo theo theme
    public static void showAlert(Alert.AlertType type, String title, String header, String content) {
        runOnFxThread(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);

            DialogPane dialogPane = alert.getDialogPane();
            try {
                String css = ClientUtils.class.getResource("/css/style.css") != null ?
                        ClientUtils.class.getResource("/css/style.css").toExternalForm() : null;
                if (css != null) {
                    dialogPane.getStylesheets().add(css);
                    dialogPane.getStyleClass().add("terminal-dialog");
                }
            } catch (Exception ignored) {}

            alert.showAndWait();
        });
    }

    // Hiển thị hộp thoại xác nhận (dùng khi hỏi ý kiến nhận file)
    public static Optional<ButtonType> showConfirmation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        DialogPane dialogPane = alert.getDialogPane();
        try {
            String css = ClientUtils.class.getResource("/css/style.css") != null ?
                    ClientUtils.class.getResource("/css/style.css").toExternalForm() : null;
            if (css != null) {
                dialogPane.getStylesheets().add(css);
                dialogPane.getStyleClass().add("terminal-dialog");
            }
        } catch (Exception ignored) {}

        return alert.showAndWait();
    }
}
