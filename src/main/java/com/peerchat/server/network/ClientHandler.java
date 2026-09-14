package com.peerchat.server.network;

import com.peerchat.server.model.ConnectedClient;
import com.peerchat.server.service.ChatService;
import com.peerchat.server.service.FileTransferService;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.model.Message;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolConstants;
import com.peerchat.shared.protocol.ProtocolMessage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

// Luồng xử lý độc lập cho từng Client: xác thực mã phiên, nhận và chuyển tiếp gói tin
public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final String expectedConnectionCode;
    private final ConnectionManager connectionManager;
    private final ChatService chatService;
    private final FileTransferService fileTransferService;

    private String clientId;
    private String displayName;
    private DataInputStream in;
    private DataOutputStream out;

    public ClientHandler(Socket socket,
                         String expectedConnectionCode,
                         ConnectionManager connectionManager,
                         ChatService chatService,
                         FileTransferService fileTransferService) {
        this.socket = socket;
        this.expectedConnectionCode = expectedConnectionCode;
        this.connectionManager = connectionManager;
        this.chatService = chatService;
        this.fileTransferService = fileTransferService;
    }

    @Override
    public void run() {
        try {
            // Bước 1: Thiết lập timeout chờ bắt tay xác thực
            socket.setSoTimeout(ProtocolConstants.SOCKET_TIMEOUT_MS);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            if (!performHandshake()) {
                return;
            }

            // Bước 2: Bắt tay thành công, bỏ timeout để giữ kết nối liên tục
            socket.setSoTimeout(0);

            // Bước 3: Vòng lặp đọc gói tin từ Client
            while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                ProtocolMessage msg;
                try {
                    msg = ProtocolMessage.readFrom(in);
                } catch (EOFException | SocketException e) {
                    LOGGER.info("[NODE_DISCONNECT] Client disconnected: " + displayName + " (" + clientId + ")");
                    break;
                }

                handleMessage(msg);
            }

        } catch (SocketTimeoutException e) {
            LOGGER.warning("[TIMEOUT] Handshake timeout from: " + socket.getRemoteSocketAddress());
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "[CLIENT_IO] Connection lost to client " + displayName + ": " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[CLIENT_ERROR] Client handling error: " + e.getMessage(), e);
        } finally {
            cleanup();
        }
    }

    // Xác thực mã kết nối Connection Code
    private boolean performHandshake() throws IOException {
        ProtocolMessage initMsg = ProtocolMessage.readFrom(in);
        if (initMsg.getType() != MessageType.CONNECT_REQUEST) {
            sendResponse(false, "INVALID_PROTOCOL: CONNECT_REQUEST required first", null);
            return false;
        }

        String payload = initMsg.getPayloadAsText();
        String code = extractParam(payload, "connectionCode");
        String name = extractParam(payload, "displayName");

        if (name == null || name.trim().isEmpty()) {
            name = "Node-" + UUID.randomUUID().toString().substring(0, 4);
        }

        // Kiểm tra mã phiên
        if (code == null || !code.trim().equalsIgnoreCase(expectedConnectionCode)) {
            LOGGER.warning("[AUTH_FAIL] Invalid connection code '" + code + "' from: " + socket.getRemoteSocketAddress());
            sendResponse(false, "ACCESS_DENIED: Invalid server connection code", null);
            return false;
        }

        // Tạo Client ID và lưu phiên kết nối
        this.clientId = UUID.randomUUID().toString();
        this.displayName = name.trim();

        ConnectedClient connectedClient = new ConnectedClient(clientId, displayName, socket, out);

        // Gửi xác nhận thành công trước để Client chuyển màn hình
        sendResponse(true, "CARRIER_ACQUIRED", clientId);

        // Gửi lịch sử tin nhắn phòng gần nhất cho client vừa vào phòng (Late Joiner Sync)
        java.util.List<Message> history = chatService.getRecentHistory();
        if (!history.isEmpty()) {
            ProtocolMessage histMsg = ProtocolMessage.createText(MessageType.CHAT_HISTORY, Message.listToJson(history));
            histMsg.writeTo(out);
        }

        // Đăng ký client vào danh sách và thông báo cho mọi người
        connectionManager.addClient(connectedClient);

        LOGGER.info("[AUTH_OK] Authentication successful: " + displayName + " [" + clientId + "]");
        return true;
    }

    // Điều hướng xử lý theo từng loại gói tin nhận được
    private void handleMessage(ProtocolMessage msg) {
        switch (msg.getType()) {
            case CHAT_MESSAGE -> {
                Message chatMsg = Message.fromJson(msg.getPayloadAsText());
                chatMsg.setSenderId(clientId);
                chatMsg.setSenderName(displayName);
                chatService.processAndRoute(chatMsg);
            }
            case FILE_METADATA -> {
                FileInfo info = FileInfo.fromJson(msg.getPayloadAsText());
                info.setSenderId(clientId);
                info.setSenderName(displayName);
                fileTransferService.handleFileUploadMetadata(info);
            }
            case FILE_DATA -> {
                fileTransferService.handleFileUploadData(msg, clientId);
            }
            case FILE_COMPLETE -> {
                FileInfo info = FileInfo.fromJson(msg.getPayloadAsText());
                info.setSenderId(clientId);
                info.setSenderName(displayName);
                fileTransferService.handleFileUploadComplete(info);
            }
            case FILE_DOWNLOAD_REQ -> {
                String payload = msg.getPayloadAsText();
                String fileId = extractParam(payload, "fileId");
                if (fileId == null || fileId.isEmpty()) {
                    fileId = FileInfo.fromJson(payload).getFileId();
                }
                fileTransferService.handleFileDownloadRequest(fileId, clientId);
            }
            case PING -> {
                try {
                    new ProtocolMessage(MessageType.PONG, new byte[0]).writeTo(out);
                } catch (IOException ignored) {}
            }
            case DISCONNECT -> {
                LOGGER.info("[NODE_QUIT] Client disconnected voluntarily: " + displayName);
                cleanup();
            }
            default -> LOGGER.warning("[UNSUPPORTED_PACKET] Packet not supported: " + msg.getType());
        }
    }

    // Gửi gói tin phản hồi kết nối ban đầu
    private void sendResponse(boolean success, String message, String assignedId) throws IOException {
        String json = "{" +
                "\"success\":" + success + "," +
                "\"message\":\"" + message + "\"," +
                "\"clientId\":\"" + (assignedId != null ? assignedId : "") + "\"" +
                "}";
        ProtocolMessage resp = ProtocolMessage.createText(MessageType.CONNECT_RESPONSE, json);
        resp.writeTo(out);
    }

    // Dọn dẹp tài nguyên khi Client ngắt kết nối (không làm crash Server)
    private void cleanup() {
        if (clientId != null) {
            connectionManager.removeClient(clientId);
        }
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    private static String extractParam(String json, String key) {
        if (json == null) return null;
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }
}
