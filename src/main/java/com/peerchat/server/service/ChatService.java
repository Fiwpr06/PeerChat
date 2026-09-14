package com.peerchat.server.service;

import com.peerchat.server.network.ConnectionManager;
import com.peerchat.shared.model.Message;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolMessage;

import java.util.logging.Logger;

// Dịch vụ định tuyến và lưu bộ đệm tin nhắn chat phía Server
public class ChatService {
    private static final Logger LOGGER = Logger.getLogger(ChatService.class.getName());
    private static final int MAX_HISTORY = 100;

    private final ConnectionManager connectionManager;
    private final java.util.List<Message> messageHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

    public ChatService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // Xử lý, lưu vào lịch sử và chuyển tiếp tin nhắn tới người nhận hoặc toàn phòng
    public void processAndRoute(Message message) {
        if (message == null || message.getContent() == null) return;

        // Lưu vào bộ đệm lịch sử (chỉ lưu tin nhắn chung ALL vào lịch sử phòng)
        if (!message.isDirect()) {
            addToHistory(message);
        }

        LOGGER.info("[CHAT] " + message.getSenderName() + " -> " + message.getTargetId() + ": " + message.getContent());

        // Đóng gói tin nhắn dưới dạng CHAT_BROADCAST
        ProtocolMessage outMsg = ProtocolMessage.createText(MessageType.CHAT_BROADCAST, message.toJson());
        connectionManager.routeMessage(message.getTargetId(), outMsg, message.getSenderId());
    }

    // Thêm một tin nhắn vào bộ đệm lịch sử
    public void addToHistory(Message message) {
        if (messageHistory.size() >= MAX_HISTORY) {
            messageHistory.remove(0);
        }
        messageHistory.add(message);
    }

    // Lấy danh sách tin nhắn lịch sử gần nhất
    public java.util.List<Message> getRecentHistory() {
        return new java.util.ArrayList<>(messageHistory);
    }
}
