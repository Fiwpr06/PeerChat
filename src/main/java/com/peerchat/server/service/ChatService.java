package com.peerchat.server.service;

import com.peerchat.server.network.ConnectionManager;
import com.peerchat.shared.model.Message;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolMessage;

import java.util.logging.Logger;

// Dịch vụ định tuyến tin nhắn chat phía Server
public class ChatService {
    private static final Logger LOGGER = Logger.getLogger(ChatService.class.getName());
    private final ConnectionManager connectionManager;

    public ChatService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // Xử lý và chuyển tiếp tin nhắn tới người nhận hoặc phát cho tất cả
    public void processAndRoute(Message message) {
        if (message == null || message.getContent() == null) return;

        LOGGER.info("[CHAT] " + message.getSenderName() + " -> " + message.getTargetId() + ": " + message.getContent());

        // Đóng gói tin nhắn dưới dạng CHAT_BROADCAST
        ProtocolMessage outMsg = ProtocolMessage.createText(MessageType.CHAT_BROADCAST, message.toJson());
        connectionManager.routeMessage(message.getTargetId(), outMsg, message.getSenderId());
    }
}
