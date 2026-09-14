package com.peerchat.server.network;

import com.peerchat.server.model.ConnectedClient;
import com.peerchat.shared.model.ClientInfo;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolConstants;
import com.peerchat.shared.protocol.ProtocolMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

// Quản lý danh sách các Client đang kết nối và điều phối gửi nhận tin nhắn
public class ConnectionManager {
    private static final Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
    private final Map<String, ConnectedClient> clients = new ConcurrentHashMap<>();

    // Giao diện lắng nghe sự kiện thêm/bớt client cho Server UI
    public interface ClientListener {
        void onClientAdded(ClientInfo info);
        void onClientRemoved(String clientId);
    }
    private final List<ClientListener> clientListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addClientListener(ClientListener l) { clientListeners.add(l); }
    public void removeClientListener(ClientListener l) { clientListeners.remove(l); }

    // Đăng ký client mới và phát danh sách online cho mọi người
    public void addClient(ConnectedClient client) {
        clients.put(client.getClientId(), client);
        LOGGER.info("[NODE_JOIN] Client connected: " + client);
        clientListeners.forEach(l -> l.onClientAdded(client.getInfo()));
        broadcastClientList();
    }

    // Xóa client khi ngắt kết nối và cập nhật lại danh sách cho các client khác
    public void removeClient(String clientId) {
        ConnectedClient client = clients.remove(clientId);
        if (client != null) {
            LOGGER.info("[NODE_LOST] Client disconnected: " + client);
            client.close();
            clientListeners.forEach(l -> l.onClientRemoved(clientId));
            broadcastClientList();
        }
    }

    public ConnectedClient getClient(String clientId) { return clients.get(clientId); }
    public List<ConnectedClient> getAllClients() { return new ArrayList<>(clients.values()); }

    public List<ClientInfo> getAllClientInfos() {
        List<ClientInfo> list = new ArrayList<>();
        for (ConnectedClient c : clients.values()) {
            list.add(c.getInfo());
        }
        return list;
    }

    public int getClientCount() { return clients.size(); }

    // Gửi danh sách các Client đang online đến toàn bộ Client
    public void broadcastClientList() {
        List<ClientInfo> infos = getAllClientInfos();
        String json = ClientInfo.listToJson(infos);
        ProtocolMessage msg = ProtocolMessage.createText(MessageType.CLIENT_LIST_UPDATE, json);
        broadcastMessage(msg, null);
    }

    // Định tuyến gói tin tới một Client cụ thể hoặc broadcast cho tất cả
    public boolean routeMessage(String targetId, ProtocolMessage msg, String senderId) {
        if (ProtocolConstants.TARGET_ALL.equalsIgnoreCase(targetId)) {
            broadcastMessage(msg, senderId);
            return true;
        }

        ConnectedClient recipient = clients.get(targetId);
        if (recipient != null) {
            try {
                recipient.sendMessage(msg);
                return true;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to send packet to " + targetId + ": " + e.getMessage());
                removeClient(targetId);
                return false;
            }
        } else {
            LOGGER.warning("Recipient node not found: " + targetId);
            return false;
        }
    }

    // Phát gói tin tới tất cả Client (có thể loại trừ Client người gửi)
    public void broadcastMessage(ProtocolMessage msg, String excludeClientId) {
        for (ConnectedClient client : clients.values()) {
            if (excludeClientId != null && excludeClientId.equals(client.getClientId())) {
                continue;
            }
            try {
                client.sendMessage(msg);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Broadcast error to " + client.getClientId() + ": " + e.getMessage());
                removeClient(client.getClientId());
            }
        }
    }

    // Đóng toàn bộ kết nối khi tắt server
    public void closeAll() {
        for (ConnectedClient client : clients.values()) {
            client.close();
        }
        clients.clear();
    }
}
