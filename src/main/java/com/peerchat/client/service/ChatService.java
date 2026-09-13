package com.peerchat.client.service;

import com.peerchat.client.network.MessageSender;
import com.peerchat.client.util.ClientUtils;
import com.peerchat.shared.model.ClientInfo;
import com.peerchat.shared.model.Message;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolConstants;
import com.peerchat.shared.protocol.ProtocolMessage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

// Dịch vụ quản lý tin nhắn và danh sách client online phía Client
public class ChatService {
    private final MessageSender sender;
    private final String clientId;
    private final String displayName;

    // Danh sách quan sát được dùng để cập nhật giao diện JavaFX tự động
    private final ObservableList<ClientInfo> onlinePeers = FXCollections.observableArrayList();
    private final ObservableList<Message> messageHistory = FXCollections.observableArrayList();

    public ChatService(MessageSender sender, String clientId, String displayName) {
        this.sender = sender;
        this.clientId = clientId;
        this.displayName = displayName;
    }

    public ObservableList<ClientInfo> getOnlinePeers() { return onlinePeers; }
    public ObservableList<Message> getMessageHistory() { return messageHistory; }
    public String getClientId() { return clientId; }
    public String getDisplayName() { return displayName; }

    // Gửi tin nhắn tới một Client cụ thể hoặc gửi tới tất cả
    public void sendMessage(String content, String targetId) {
        if (content == null || content.trim().isEmpty()) return;

        String target = (targetId != null && !targetId.isEmpty()) ? targetId : ProtocolConstants.TARGET_ALL;
        Message msg = new Message(clientId, displayName, target, content.trim());

        // Hiển thị ngay tin nhắn gửi lên giao diện của mình
        ClientUtils.runOnFxThread(() -> messageHistory.add(msg));

        // Đóng gói và gửi qua socket tới Server
        ProtocolMessage protoMsg = ProtocolMessage.createText(MessageType.CHAT_MESSAGE, msg.toJson());
        sender.sendAsync(protoMsg);
    }

    // Nhận tin nhắn mới từ Server và thêm vào lịch sử hiển thị
    public void onChatMessageReceived(Message message) {
        if (clientId.equals(message.getSenderId())) return; // Bỏ qua nếu là tin nhắn của chính mình
        ClientUtils.runOnFxThread(() -> messageHistory.add(message));
    }

    // Cập nhật danh sách các Client đang online (loại bỏ chính mình khỏi danh sách hiển thị)
    public void updateOnlinePeers(List<ClientInfo> peers) {
        ClientUtils.runOnFxThread(() -> {
            onlinePeers.clear();
            for (ClientInfo info : peers) {
                if (!info.getClientId().equals(clientId)) {
                    onlinePeers.add(info);
                }
            }
        });
    }
}
