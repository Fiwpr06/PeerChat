package com.peerchat.server.network;

import com.peerchat.server.model.ConnectedClient;
import com.peerchat.shared.model.ClientInfo;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolConstants;
import com.peerchat.shared.protocol.ProtocolMessage;

import com.peerchat.shared.model.GroupInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

// Quản lý danh sách các Client đang kết nối và điều phối gửi nhận tin nhắn
public class ConnectionManager {
    private static final Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
    private final Map<String, ConnectedClient> clients = new ConcurrentHashMap<>();

    // Quản lý các nhóm Multicast tầng ứng dụng (ALM Group Directory)
    private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groupMembers = new ConcurrentHashMap<>();

    // Giao diện lắng nghe sự kiện thêm/bớt client cho Server UI
    public interface ClientListener {
        void onClientAdded(ClientInfo info);
        void onClientRemoved(String clientId);
    }
    private final List<ClientListener> clientListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addClientListener(ClientListener l) { clientListeners.add(l); }
    public void removeClientListener(ClientListener l) { clientListeners.remove(l); }

    // Đăng ký client mới, gửi danh sách nhóm hiện có và phát danh sách online cho mọi người
    public void addClient(ConnectedClient client) {
        clients.put(client.getClientId(), client);
        LOGGER.info("[NODE_JOIN] Client connected: " + client);
        clientListeners.forEach(l -> l.onClientAdded(client.getInfo()));
        broadcastClientList();
        if (!groups.isEmpty()) {
            sendGroupListTo(client);
        }
    }

    // Xóa client khi ngắt kết nối và cập nhật lại danh sách cho các client khác
    public void removeClient(String clientId) {
        ConnectedClient client = clients.remove(clientId);
        if (client != null) {
            LOGGER.info("[NODE_LOST] Client disconnected: " + client);
            client.close();
            clientListeners.forEach(l -> l.onClientRemoved(clientId));
            broadcastClientList();

            // Dọn dẹp thành viên trong các nhóm Multicast
            boolean groupChanged = false;
            for (Map.Entry<String, Set<String>> entry : groupMembers.entrySet()) {
                if (entry.getValue().remove(clientId)) {
                    GroupInfo info = groups.get(entry.getKey());
                    if (info != null) info.removeMember(clientId);
                    groupChanged = true;
                    if (entry.getValue().isEmpty()) {
                        groups.remove(entry.getKey());
                        groupMembers.remove(entry.getKey());
                    } else if (info != null && clientId.equals(info.getCreatorId())) {
                        String nextAdmin = entry.getValue().iterator().next();
                        info.setCreatorId(nextAdmin);
                    }
                }
            }
            if (groupChanged) {
                broadcastGroupList();
            }
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

    // ==========================================
    // QUẢN LÝ NHÓM MULTICAST TẦNG ỨNG DỤNG (ALM)
    // ==========================================

    public GroupInfo createGroup(String groupName, String creatorId) {
        String groupId = "grp_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String name = (groupName != null && !groupName.trim().isEmpty()) ? groupName.trim() : "Nhóm-" + groupId.substring(4);
        GroupInfo info = new GroupInfo(groupId, name, creatorId);
        groups.put(groupId, info);

        Set<String> members = ConcurrentHashMap.newKeySet();
        if (creatorId != null && !creatorId.isEmpty()) {
            members.add(creatorId);
        }
        groupMembers.put(groupId, members);

        LOGGER.info("[GROUP_CREATED] Group created: " + info);
        broadcastGroupList();
        return info;
    }

    public boolean joinGroup(String groupId, String clientId) {
        if (groupId == null || clientId == null) return false;
        Set<String> members = groupMembers.get(groupId);
        GroupInfo info = groups.get(groupId);
        if (members == null || info == null) return false;

        members.add(clientId);
        info.addMember(clientId);
        LOGGER.info("[GROUP_JOINED] Client " + clientId + " joined " + groupId);
        broadcastGroupList();
        return true;
    }

    public void leaveGroup(String groupId, String clientId) {
        if (groupId == null || clientId == null) return;
        Set<String> members = groupMembers.get(groupId);
        GroupInfo info = groups.get(groupId);
        if (members != null) {
            members.remove(clientId);
            if (info != null) {
                info.removeMember(clientId);
            }
            LOGGER.info("[GROUP_LEFT] Client " + clientId + " left " + groupId);
            if (members.isEmpty()) {
                groups.remove(groupId);
                groupMembers.remove(groupId);
                LOGGER.info("[GROUP_REMOVED] Group " + groupId + " was removed (empty members)");
            } else if (info != null && clientId.equals(info.getCreatorId())) {
                String nextAdmin = members.iterator().next();
                info.setCreatorId(nextAdmin);
                LOGGER.info("[GROUP_ADMIN_TRANSFERRED] Admin of " + groupId + " transferred to " + nextAdmin);
            }
            broadcastGroupList();
        }
    }

    public boolean addUserToGroup(String groupId, String targetClientId, String requesterId) {
        if (groupId == null || targetClientId == null || requesterId == null) return false;
        GroupInfo info = groups.get(groupId);
        Set<String> members = groupMembers.get(groupId);
        if (info == null || members == null) return false;

        // Chỉ Admin (người tạo nhóm) mới có quyền thêm thành viên
        if (!requesterId.equals(info.getCreatorId())) {
            LOGGER.warning("[GROUP_ADD_DENIED] Client " + requesterId + " is not creator of " + groupId);
            return false;
        }

        if (!clients.containsKey(targetClientId)) {
            LOGGER.warning("[GROUP_ADD_FAIL] Target " + targetClientId + " is not connected");
            return false;
        }

        members.add(targetClientId);
        info.addMember(targetClientId);
        LOGGER.info("[GROUP_USER_ADDED] Client " + targetClientId + " added to " + groupId + " by " + requesterId);
        broadcastGroupList();
        return true;
    }

    public boolean kickUserFromGroup(String groupId, String targetClientId, String requesterId) {
        if (groupId == null || targetClientId == null || requesterId == null) return false;
        GroupInfo info = groups.get(groupId);
        Set<String> members = groupMembers.get(groupId);
        if (info == null || members == null) return false;

        // Chỉ Admin (người tạo nhóm) mới có quyền kích thành viên
        if (!requesterId.equals(info.getCreatorId())) {
            LOGGER.warning("[GROUP_KICK_DENIED] Client " + requesterId + " is not creator of " + groupId);
            return false;
        }

        if (targetClientId.equals(info.getCreatorId())) {
            LOGGER.warning("[GROUP_KICK_DENIED] Creator cannot kick themselves: " + targetClientId);
            return false;
        }

        members.remove(targetClientId);
        info.removeMember(targetClientId);
        LOGGER.info("[GROUP_USER_KICKED] Client " + targetClientId + " kicked from " + groupId + " by " + requesterId);
        broadcastGroupList();
        return true;
    }

    public boolean isGroup(String targetId) {
        return targetId != null && groups.containsKey(targetId);
    }

    public GroupInfo getGroup(String groupId) {
        return groups.get(groupId);
    }

    public List<GroupInfo> getAllGroups() {
        return new ArrayList<>(groups.values());
    }

    // Lấy danh sách các nhóm mà client đang là thành viên (Nhóm kín)
    public List<GroupInfo> getGroupsForClient(String clientId) {
        List<GroupInfo> list = new ArrayList<>();
        if (clientId == null) return list;
        for (GroupInfo g : groups.values()) {
            if (g.hasMember(clientId)) {
                list.add(g);
            }
        }
        return list;
    }

    // Gửi danh sách nhóm phù hợp tới từng client riêng lẻ (chỉ gửi nhóm client thuộc về)
    public void broadcastGroupList() {
        for (ConnectedClient client : clients.values()) {
            sendGroupListTo(client);
        }
    }

    public void sendGroupListTo(ConnectedClient client) {
        if (client != null) {
            List<GroupInfo> list = getGroupsForClient(client.getClientId());
            String json = GroupInfo.listToJson(list);
            ProtocolMessage msg = ProtocolMessage.createText(MessageType.GROUP_LIST_UPDATE, json);
            try {
                client.sendMessage(msg);
            } catch (IOException ignored) {}
        }
    }

    // Phát gói tin đa hướng (Multicast) tới tất cả thành viên trong nhóm qua các kết nối TCP Unicast
    public void multicastToGroup(String groupId, ProtocolMessage msg, String excludeClientId) {
        Set<String> members = groupMembers.get(groupId);
        if (members == null) return;

        for (String memberId : members) {
            if (excludeClientId != null && excludeClientId.equals(memberId)) {
                continue;
            }
            ConnectedClient recipient = clients.get(memberId);
            if (recipient != null) {
                try {
                    recipient.sendMessage(msg);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Multicast error to member " + memberId + ": " + e.getMessage());
                    removeClient(memberId);
                }
            }
        }
    }

    // Định tuyến gói tin: Toàn phòng (ALL), Nhóm Multicast, hoặc Client Unicast 1-1
    public boolean routeMessage(String targetId, ProtocolMessage msg, String senderId) {
        if (ProtocolConstants.TARGET_ALL.equalsIgnoreCase(targetId)) {
            broadcastMessage(msg, senderId);
            return true;
        }

        if (isGroup(targetId)) {
            multicastToGroup(targetId, msg, senderId);
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
        groups.clear();
        groupMembers.clear();
    }
}
