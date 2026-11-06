package com.peerchat.server.service;

import com.peerchat.server.db.DataStoreManager;
import com.peerchat.server.network.ConnectionManager;
import com.peerchat.shared.model.Message;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolMessage;

import java.util.List;
import java.util.logging.Logger;

// Dịch vụ định tuyến và lưu trữ tin nhắn chat phía Server (kết hợp MySQL ngầm)
public class ChatService {
    private static final Logger LOGGER = Logger.getLogger(ChatService.class.getName());
    private static final int MAX_HISTORY = 100;

    private final ConnectionManager connectionManager;
    private final DataStoreManager dataStoreManager;

    public ChatService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.dataStoreManager = DataStoreManager.getInstance();
    }

    // Xử lý, chuyển tiếp tin nhắn tức thì và lưu vào CSDL ngầm
    public void processAndRoute(Message message) {
        if (message == null || message.getContent() == null) return;

        LOGGER.info("[CHAT] " + message.getSenderName() + " -> " + message.getTargetId() + ": " + message.getContent());

        // 1. Chuyển tiếp tin nhắn qua TCP Socket NGAY LẬP TỨC (ưu tiên độ trễ thời gian thực)
        ProtocolMessage outMsg = ProtocolMessage.createText(MessageType.CHAT_BROADCAST, message.toJson());
        connectionManager.routeMessage(message.getTargetId(), outMsg, message.isFileMessage() ? null : message.getSenderId());

        // 2. Đẩy tác vụ lưu CSDL vào hàng đợi bất đồng bộ (chạy ngầm không chặn Socket)
        dataStoreManager.getAsyncDbWorker().submit(() -> {
            dataStoreManager.getStore().saveMessage(message);
        });
    }

    // Lấy danh sách tin nhắn lịch sử gần nhất cho Late Joiner Sync từ CSDL
    public List<Message> getRecentHistory() {
        return dataStoreManager.getStore().getRecentMessages(MAX_HISTORY);
    }
}
