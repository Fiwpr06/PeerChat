package com.peerchat.server.controller;

import com.peerchat.server.Server;
import com.peerchat.server.network.ConnectionManager;
import com.peerchat.shared.model.ClientInfo;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

// Điều khiển giao diện quản trị Server và theo dõi kết nối
public class ServerController {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Bản ghi thông tin client hiển thị trên bảng
    public record ClientRow(String name, String address, String time, String clientId) {}

    @FXML private Label statusPillLabel;
    @FXML private Button serverToggleBtn;
    @FXML private TextField portField;
    @FXML private Label ipDisplayLabel;
    @FXML private Label codeDisplayLabel;
    @FXML private Button copyCodeBtn;
    @FXML private Label copyFeedbackLabel;

    @FXML private Label clientCountLabel;
    @FXML private TableView<ClientRow> clientsTableView;
    @FXML private TableColumn<ClientRow, String> colName;
    @FXML private TableColumn<ClientRow, String> colAddress;
    @FXML private TableColumn<ClientRow, String> colTime;

    @FXML private TextArea logTextArea;
    @FXML private Label footerClientsLabel;
    @FXML private Label footerStorageLabel;
    @FXML private Label serverClockLabel;

    private final ObservableList<ClientRow> clientRows = FXCollections.observableArrayList();
    private Server server;
    private Thread serverThread;
    private Timeline clockTimeline;
    private Timeline feedbackTimeline;
    private Handler logHandler;

    // Khởi tạo các thành phần giao diện Server
    @FXML
    public void initialize() {
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        colAddress.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().address()));
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().time()));
        clientsTableView.setItems(clientRows);

        String lanIp = Server.getLocalLanAddress();
        ipDisplayLabel.setText("127.0.0.1 | LAN: " + lanIp);

        setupLogCapture();
        startClock();

        // Tự động khởi động Server ngay khi mở giao diện
        handleToggleServer();
    }

    // Bật/tắt trạng thái hoạt động của Server
    @FXML
    private void handleToggleServer() {
        if (server != null && server.isRunning()) {
            stopServerInstance();
        } else {
            startServerInstance();
        }
    }

    // Khởi động phiên Server mới trên cổng đã chọn
    private void startServerInstance() {
        int port = 5000;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port <= 0 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            appendLog("[ERROR] Cổng không hợp lệ (1-65535). Đặt lại 5000.");
            port = 5000;
            portField.setText("5000");
        }

        server = new Server(port);

        // Lắng nghe sự kiện kết nối client
        server.getConnectionManager().addClientListener(new ConnectionManager.ClientListener() {
            @Override
            public void onClientAdded(ClientInfo info) {
                Platform.runLater(() -> {
                    String time = LocalDateTime.now().format(TIME_FORMAT);
                    clientRows.add(new ClientRow(info.getDisplayName(), info.getCoordinates(), time, info.getClientId()));
                    updateStats();
                });
            }

            @Override
            public void onClientRemoved(String clientId) {
                Platform.runLater(() -> {
                    clientRows.removeIf(row -> row.clientId().equals(clientId));
                    updateStats();
                });
            }
        });

        serverThread = new Thread(server::start, "Server-Worker-Thread");
        serverThread.setDaemon(true);
        serverThread.start();

        // Chờ server gán mã và bind socket
        new Thread(() -> {
            int wait = 0;
            while (!server.isRunning() && wait < 30) {
                try { Thread.sleep(100); } catch (Exception ignored) {}
                wait++;
            }
            Platform.runLater(() -> {
                if (server.isRunning()) {
                    statusPillLabel.setText("ĐANG CHẠY");
                    statusPillLabel.getStyleClass().setAll("server-status-pill-running");
                    serverToggleBtn.setText("⏹ DỪNG SERVER");
                    serverToggleBtn.getStyleClass().setAll("button-danger");
                    portField.setDisable(true);
                    codeDisplayLabel.setText(server.getConnectionCode());
                    appendLog("[SYSTEM] Server running on port " + server.getPort() + " with code: " + server.getConnectionCode());
                    updateStats();
                } else {
                    appendLog("[ERROR] Could not start server on port " + server.getPort());
                }
            });
        }).start();
    }

    // Dừng Server và đóng toàn bộ kết nối hiện hành
    private void stopServerInstance() {
        if (server != null) {
            server.stop();
            server = null;
        }
        clientRows.clear();
        statusPillLabel.setText("ĐANG DỪNG");
        statusPillLabel.getStyleClass().setAll("server-status-pill-stopped");
        serverToggleBtn.setText("▶ KHỞI ĐỘNG SERVER");
        serverToggleBtn.getStyleClass().setAll("button-action");
        portField.setDisable(false);
        codeDisplayLabel.setText("-----");
        updateStats();
        appendLog("[SYSTEM] Server has been stopped.");
    }

    // Sao chép mã phòng vào Clipboard của hệ điều hành
    @FXML
    private void handleCopyCode() {
        String code = codeDisplayLabel.getText();
        if (code != null && !code.equals("-----")) {
            ClipboardContent content = new ClipboardContent();
            content.putString(code);
            Clipboard.getSystemClipboard().setContent(content);

            showFeedback("✓ ĐÃ SAO CHÉP MÃ PHÒNG: " + code);
        }
    }

    // Sao chép địa chỉ IP mạng LAN vào Clipboard
    @FXML
    private void handleCopyIp() {
        String ip = Server.getLocalLanAddress();
        ClipboardContent content = new ClipboardContent();
        content.putString(ip);
        Clipboard.getSystemClipboard().setContent(content);

        showFeedback("✓ ĐÃ SAO CHÉP ĐỊA CHỈ IP: " + ip);
    }

    // Ngắt kết nối client đang được chọn trong danh sách
    @FXML
    private void handleDisconnectSelectedClient() {
        ClientRow selected = clientsTableView.getSelectionModel().getSelectedItem();
        if (selected != null && server != null) {
            server.getConnectionManager().removeClient(selected.clientId());
            appendLog("[ADMIN] Đã ngắt kết nối client: " + selected.name() + " (" + selected.address() + ")");
        }
    }

    // Xóa trắng nhật ký hoạt động
    @FXML
    private void handleClearLog() {
        logTextArea.clear();
    }

    // Hiển thị thông báo phản hồi tạm thời
    private void showFeedback(String message) {
        copyFeedbackLabel.setText(message);
        if (feedbackTimeline != null) {
            feedbackTimeline.stop();
        }
        feedbackTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> copyFeedbackLabel.setText("")));
        feedbackTimeline.play();
    }

    // Ghi nhật ký ra khung hiển thị log
    private void appendLog(String message) {
        Platform.runLater(() -> {
            String time = LocalDateTime.now().format(TIME_FORMAT);
            logTextArea.appendText("[" + time + "] " + message + "\n");
        });
    }

    // Thu thập các log phát ra từ Server package và đưa lên UI
    private void setupLogCapture() {
        Logger serverLogger = Logger.getLogger("com.peerchat.server");
        logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record != null && record.getMessage() != null) {
                    appendLog(record.getMessage());
                }
            }

            @Override public void flush() {}
            @Override public void close() {}
        };
        serverLogger.addHandler(logHandler);
    }

    // Cập nhật số liệu thống kê ở chân trang
    private void updateStats() {
        int count = clientRows.size();
        clientCountLabel.setText(count + " NODES");
        footerClientsLabel.setText("Nodes: " + count + " trực tuyến");

        File storageDir = new File("server_storage");
        int fileCount = 0;
        long totalBytes = 0;
        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] files = storageDir.listFiles();
            if (files != null) {
                fileCount = files.length;
                for (File f : files) totalBytes += f.length();
            }
        }
        footerStorageLabel.setText(String.format("Kho lưu trữ: %d tệp (%s)",
                fileCount, com.peerchat.shared.util.FileUtils.formatFileSize(totalBytes)));
    }

    // Đồng hồ hệ thống thời gian thực
    private void startClock() {
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            serverClockLabel.setText("GIỜ HT: " + LocalDateTime.now().format(TIME_FORMAT));
        }));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }

    // Dọn dẹp tài nguyên khi đóng ứng dụng
    public void cleanup() {
        if (clockTimeline != null) clockTimeline.stop();
        if (feedbackTimeline != null) feedbackTimeline.stop();
        if (server != null) {
            server.stop();
        }
    }
}