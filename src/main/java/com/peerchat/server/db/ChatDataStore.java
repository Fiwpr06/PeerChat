package com.peerchat.server.db;

import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.model.GroupInfo;
import com.peerchat.shared.model.Message;

import java.util.List;

// Giao diện trừu tượng hóa tầng lưu trữ dữ liệu (Data Access Object / Repository)
// Cho phép Server chuyển đổi linh hoạt giữa MySQL Database và InMemory Fallback mà không ảnh hưởng Socket Core
public interface ChatDataStore {

    // Khởi tạo kết nối và cấu trúc dữ liệu
    boolean init();

    // Kiểm tra kho lưu trữ có đang hoạt động bình thường không
    boolean isAvailable();

    // Trả về tên định danh kiểu lưu trữ ("MYSQL" hoặc "IN_MEMORY")
    String getStoreType();

    // Lưu một tin nhắn mới
    void saveMessage(Message message);

    // Lấy danh sách tin nhắn gần nhất cho Late Joiner Sync
    List<Message> getRecentMessages(int limit);

    // Lưu siêu dữ liệu tập tin đã tải lên thành công
    void saveFile(FileInfo fileInfo, String storedPath);

    // Lấy thông tin tập tin theo fileId
    FileInfo getFile(String fileId);

    // Lưu hoặc cập nhật nhóm chat Multicast
    void saveGroup(GroupInfo group);

    // Thêm thành viên vào nhóm
    void addGroupMember(String groupId, String userId);

    // Xóa thành viên khỏi nhóm
    void removeGroupMember(String groupId, String userId);

    // Xóa nhóm khi không còn thành viên
    void deleteGroup(String groupId);

    // Lấy toàn bộ danh sách các nhóm
    List<GroupInfo> getAllGroups();

    // Đóng kết nối khi Server tắt
    void close();
}
