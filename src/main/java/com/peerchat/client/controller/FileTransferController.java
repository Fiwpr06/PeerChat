package com.peerchat.client.controller;

import com.peerchat.client.service.ChatService;
import com.peerchat.client.service.FileTransferService;
import com.peerchat.client.util.ClientUtils;
import com.peerchat.shared.model.ClientInfo;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.util.FileUtils;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;

// Điều khiển panel Kho Tài Liệu Phòng (danh sách tập tin đã chia sẻ và tiến độ tải)
public class FileTransferController {
    @FXML private VBox repoRootPanel;
    @FXML private Label fileCountBadge;
    @FXML private ListView<FileInfo> sharedFilesListView;
    @FXML private Label speedLabel;
    @FXML private Label statusLabel;
    @FXML private Label checksumLabel;
    @FXML private ProgressBar transferProgressBar;

    private FileTransferService fileTransferService;
    private ChatService chatService;
    private Runnable closeCallback;

    // Khởi tạo dịch vụ và danh sách tập tin phòng
    public void initData(FileTransferService fileTransferService, ChatService chatService, Runnable closeCallback) {
        this.fileTransferService = fileTransferService;
        this.chatService = chatService;
        this.closeCallback = closeCallback;

        // Ràng buộc thanh telemetry tiến trình
        transferProgressBar.progressProperty().bind(fileTransferService.progressProperty());
        speedLabel.textProperty().bind(fileTransferService.telemetrySpeedProperty());
        statusLabel.textProperty().bind(fileTransferService.statusTextProperty());
        checksumLabel.textProperty().bind(fileTransferService.checksumResultProperty());

        // Gắn danh sách tập tin phòng
        sharedFilesListView.setItems(chatService.getSharedFiles());
        updateBadge();

        chatService.getSharedFiles().addListener((ListChangeListener<FileInfo>) c -> updateBadge());

        // Định dạng hiển thị từng dòng tập tin trong kho
        sharedFilesListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FileInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox cellBox = new VBox(4);
                    cellBox.setStyle("-fx-padding: 6px 8px; -fx-background-color: transparent;");

                    HBox topRow = new HBox(6);
                    topRow.setAlignment(Pos.CENTER_LEFT);

                    String icon = getSimpleFileIcon(item.getFileName());
                    Label iconLabel = new Label(icon);
                    iconLabel.setStyle("-fx-font-size: 12px;");

                    Label nameLabel = new Label(item.getFileName());
                    nameLabel.setStyle("-fx-text-fill: #1c1b17; -fx-font-weight: bold; -fx-font-size: 11px;");
                    nameLabel.setMaxWidth(180);

                    topRow.getChildren().addAll(iconLabel, nameLabel);

                    HBox bottomRow = new HBox(6);
                    bottomRow.setAlignment(Pos.CENTER_LEFT);

                    Label metaLabel = new Label(FileUtils.formatFileSize(item.getFileSize()) + " • " + item.getSenderName());
                    metaLabel.setStyle("-fx-text-fill: #615c4f; -fx-font-size: 10px;");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                    Button downloadBtn = new Button("⬇ TẢI VỀ");
                    downloadBtn.getStyleClass().add("file-card-btn");
                    downloadBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2px 8px;");

                    downloadBtn.setOnAction(e -> {
                        FileChooser chooser = new FileChooser();
                        chooser.setTitle("CHỌN NƠI LƯU TẬP TIN");
                        chooser.setInitialFileName(item.getFileName());
                        File dest = chooser.showSaveDialog(repoRootPanel.getScene().getWindow());

                        if (dest != null) {
                            downloadBtn.setDisable(true);
                            downloadBtn.setText("ĐANG TẢI...");
                            fileTransferService.requestDownload(item, dest, new FileTransferService.DownloadCallback() {
                                @Override
                                public void onProgress(double progress, long currentBytes, long totalBytes) {}

                                @Override
                                public void onComplete(boolean success, String hash, String error) {
                                    ClientUtils.runOnFxThread(() -> {
                                        if (success) {
                                            downloadBtn.setText("📂 MỞ THƯ MỤC");
                                            downloadBtn.setDisable(false);
                                            downloadBtn.setOnAction(ev -> {
                                                try {
                                                    if (dest.getParentFile() != null && dest.getParentFile().exists()) {
                                                        java.awt.Desktop.getDesktop().open(dest.getParentFile());
                                                    }
                                                } catch (Exception ignored) {}
                                            });
                                        } else if ("FILE_NOT_FOUND".equals(error)) {
                                            downloadBtn.setText("FILE ĐÃ MẤT");
                                            downloadBtn.setDisable(true);
                                        } else {
                                            downloadBtn.setText("TẢI LẠI");
                                            downloadBtn.setDisable(false);
                                        }
                                    });
                                }
                            });
                        }
                    });

                    bottomRow.getChildren().addAll(metaLabel, spacer, downloadBtn);
                    cellBox.getChildren().addAll(topRow, bottomRow);
                    setGraphic(cellBox);
                }
            }
        });
    }

    public void initService(FileTransferService fileTransferService) {
        this.fileTransferService = fileTransferService;
    }

    public void setSelectedTarget(ClientInfo peer) {}

    // Đóng hoặc ẩn panel kho tài liệu
    @FXML
    private void handleClosePanel() {
        if (closeCallback != null) {
            closeCallback.run();
        }
    }

    private void updateBadge() {
        if (fileCountBadge != null && chatService != null) {
            int count = chatService.getSharedFiles().size();
            fileCountBadge.setText(count + " TỆP");
        }
    }

    // Kiểm tra định dạng tập tin có phải là hình ảnh hay không
    public static boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    // Biểu tượng đơn giản cho từng loại tập tin
    public static String getSimpleFileIcon(String fileName) {
        return isImageFile(fileName) ? "🖼" : "🗎";
    }
}
