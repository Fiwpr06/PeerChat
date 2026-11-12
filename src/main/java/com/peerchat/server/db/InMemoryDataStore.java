package com.peerchat.server.db;

import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.model.GroupInfo;
import com.peerchat.shared.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Triển khai lưu trữ tạm thời trên RAM (Bộ nhớ trong)
// Dùng khi Server chạy ở chế độ nhẹ độc lập hoặc làm Graceful Fallback nếu MySQL gặp sự cố
public class InMemoryDataStore implements ChatDataStore {

    private final List<Message> messageHistory = new CopyOnWriteArrayList<>();
    private final Map<String, FileInfo> storedFiles = new ConcurrentHashMap<>();
    private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>();

    @Override
    public boolean init() {
        // RAM data store luôn sẵn sàng
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getStoreType() {
        return "IN_MEMORY";
    }

    @Override
    public void saveMessage(Message message) {
        if (message == null) return;
        // Giới hạn 200 tin gần nhất trên RAM để tránh phình bộ nhớ
        if (messageHistory.size() >= 200) {
            messageHistory.remove(0);
        }
        messageHistory.add(message);
    }

    @Override
    public List<Message> getRecentMessages(int limit) {
        List<Message> publicOnly = new ArrayList<>();
        for (Message m : messageHistory) {
            if (m.getTargetId() != null && "ALL".equalsIgnoreCase(m.getTargetId())) {
                publicOnly.add(m);
            }
        }
        int size = publicOnly.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(publicOnly.subList(fromIndex, size));
    }

    @Override
    public void saveFile(FileInfo fileInfo, String storedPath) {
        if (fileInfo != null && fileInfo.getFileId() != null) {
            storedFiles.put(fileInfo.getFileId(), fileInfo);
        }
    }

    @Override
    public FileInfo getFile(String fileId) {
        return (fileId != null) ? storedFiles.get(fileId) : null;
    }

    @Override
    public void saveGroup(GroupInfo group) {
        if (group != null && group.getGroupId() != null) {
            groups.put(group.getGroupId(), group);
        }
    }

    @Override
    public void addGroupMember(String groupId, String userId) {
        GroupInfo g = groups.get(groupId);
        if (g != null && userId != null) {
            g.addMember(userId);
        }
    }

    @Override
    public void removeGroupMember(String groupId, String userId) {
        GroupInfo g = groups.get(groupId);
        if (g != null && userId != null) {
            g.removeMember(userId);
        }
    }

    @Override
    public void deleteGroup(String groupId) {
        if (groupId != null) {
            groups.remove(groupId);
        }
    }

    @Override
    public List<GroupInfo> getAllGroups() {
        return new ArrayList<>(groups.values());
    }

    @Override
    public void addJoinRequest(String groupId, String userId) {
        GroupInfo g = groups.get(groupId);
        if (g != null && userId != null) {
            g.addJoinRequest(userId);
        }
    }

    @Override
    public void removeJoinRequest(String groupId, String userId) {
        GroupInfo g = groups.get(groupId);
        if (g != null && userId != null) {
            g.removeJoinRequest(userId);
        }
    }

    @Override
    public void updateGroupPublicStatus(String groupId, boolean isPublic) {
        GroupInfo g = groups.get(groupId);
        if (g != null) {
            g.setPublic(isPublic);
        }
    }

    @Override
    public void close() {
        messageHistory.clear();
        storedFiles.clear();
        groups.clear();
    }
}
