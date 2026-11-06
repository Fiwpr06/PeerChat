package com.peerchat.server.db;

import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.model.GroupInfo;
import com.peerchat.shared.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

// Kho lưu trữ tạm thời trên RAM (In-Memory) sử dụng khi MySQL không khả dụng
// Đảm bảo Server chạy an toàn 100%, không bao giờ bị dừng đột ngột
public class InMemoryDataStore implements ChatDataStore {
    private static final Logger LOGGER = Logger.getLogger(InMemoryDataStore.class.getName());
    private static final int MAX_HISTORY = 100;

    private final List<Message> messageHistory = new CopyOnWriteArrayList<>();
    private final Map<String, FileInfo> storedFiles = new ConcurrentHashMap<>();
    private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>();

    @Override
    public boolean init() {
        LOGGER.info("[IN_MEMORY_STORE] Khởi tạo kho lưu trữ In-Memory thành công.");
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
        if (messageHistory.size() >= MAX_HISTORY) {
            messageHistory.remove(0);
        }
        messageHistory.add(message);
    }

    @Override
    public List<Message> getRecentMessages(int limit) {
        int size = messageHistory.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(messageHistory.subList(fromIndex, size));
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
    public void close() {
        messageHistory.clear();
        storedFiles.clear();
        groups.clear();
    }
}
