package com.peerchat.client.controller;

import com.peerchat.client.Main;
import com.peerchat.client.network.ClientSocket;
import com.peerchat.client.network.MessageReceiver;
import com.peerchat.client.network.MessageSender;
import com.peerchat.client.service.ChatService;
import com.peerchat.client.service.FileTransferService;
import com.peerchat.client.util.ClientUtils;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolMessage;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

// Điều khiển màn hình đăng nhập và kết nối server
public class ConnectController {
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private Label statusLabel;
    @FXML private Button connectButton;

    // Xử lý sự kiện khi nhấn nút kết nối
    @FXML
    private void handleConnect() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String code = codeField.getText().trim().toUpperCase();
        String name = nameField.getText().trim();

        if (host.isEmpty()) {
            statusLabel.setText("Lỗi: Địa chỉ IP/Host không được để trống.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port <= 0 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setText("Lỗi: Cổng không hợp lệ (1 - 65535).");
            return;
        }

        if (code.isEmpty()) {
            statusLabel.setText("Lỗi: Vui lòng nhập mã phòng 5 ký tự.");
            return;
        }

        if (name.isEmpty()) {
            statusLabel.setText("Lỗi: Vui lòng nhập tên hiển thị.");
            return;
        }

        connectButton.setDisable(true);
        statusLabel.setText("Đang kết nối đến máy chủ...");

        // Kết nối bất đồng bộ để tránh đơ giao diện
        new Thread(() -> {
            ClientSocket clientSocket = new ClientSocket();
            try {
                clientSocket.connect(host, port, 10000);

                MessageSender sender = new MessageSender(clientSocket.getOutputStream());

                // Gửi yêu cầu xác thực kết nối
                String reqJson = "{\"connectionCode\":\"" + code + "\",\"displayName\":\"" + name + "\"}";
                ProtocolMessage reqMsg = ProtocolMessage.createText(MessageType.CONNECT_REQUEST, reqJson);
                sender.sendSync(reqMsg);

                // Chờ phản hồi kết quả từ server
                ProtocolMessage respMsg;
                java.util.List<com.peerchat.shared.model.ClientInfo> earlyPeerList = null;
                while (true) {
                    respMsg = ProtocolMessage.readFrom(clientSocket.getInputStream());
                    if (respMsg.getType() == MessageType.CONNECT_RESPONSE) {
                        break;
                    } else if (respMsg.getType() == MessageType.CLIENT_LIST_UPDATE) {
                        earlyPeerList = com.peerchat.shared.model.ClientInfo.listFromJson(respMsg.getPayloadAsText());
                    } else {
                        throw new IOException("Phản hồi không hợp lệ từ máy chủ: " + respMsg.getType());
                    }
                }

                String respJson = respMsg.getPayloadAsText();
                boolean success = respJson.contains("\"success\":true");
                String assignedId = extractParam(respJson, "clientId");
                String serverMsg = extractParam(respJson, "message");

                if (!success) {
                    clientSocket.disconnect();
                    ClientUtils.runOnFxThread(() -> {
                        statusLabel.setText("Từ chối: " + (serverMsg != null ? serverMsg : "Mã phòng không đúng"));
                        connectButton.setDisable(false);
                    });
                    return;
                }

                // Kết nối thành công, khởi tạo các dịch vụ chat và truyền file
                MessageReceiver receiver = new MessageReceiver(clientSocket.getInputStream());
                ChatService chatService = new ChatService(sender, assignedId, name);
                if (earlyPeerList != null) {
                    chatService.updateOnlinePeers(earlyPeerList);
                }
                FileTransferService fileTransferService = new FileTransferService(sender, assignedId, name);

                // Chuyển sang giao diện chat chính
                ClientUtils.runOnFxThread(() -> {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
                        Parent root = loader.load();

                        ChatController controller = loader.getController();
                        controller.initData(clientSocket, sender, receiver, chatService, fileTransferService, host + ":" + port);

                        Stage stage = (Stage) connectButton.getScene().getWindow();
                        Scene scene = new Scene(root, 1080, 720);
                        stage.setScene(scene);
                        stage.setTitle("PEERCHAT // " + name + " [" + host + ":" + port + "]");
                        stage.centerOnScreen();

                    } catch (IOException e) {
                        statusLabel.setText("Lỗi mở giao diện chat: " + e.getMessage());
                        connectButton.setDisable(false);
                    }
                });

            } catch (IOException e) {
                clientSocket.disconnect();
                ClientUtils.runOnFxThread(() -> {
                    statusLabel.setText("Lỗi kết nối: " + e.getMessage());
                    connectButton.setDisable(false);
                });
            }
        }, "PeerChat-ConnectWorker").start();
    }

    // Trích xuất giá trị trường trong chuỗi JSON đơn giản
    private static String extractParam(String json, String key) {
        if (json == null) return "";
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) return m.group(1);
        return "";
    }
}
