package com.peerchat.server.db;

import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.model.GroupInfo;
import com.peerchat.shared.model.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

// Kho lưu trữ CSDL MySQL cho PeerChat
// Tự động kiểm tra kết nối, tạo CSDL và bảng với chuẩn utf8mb4 hỗ trợ tiếng Việt và Emoji
public class MySqlDataStore implements ChatDataStore {
    private static final Logger LOGGER = Logger.getLogger(MySqlDataStore.class.getName());

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 3306;
    private static final String DB_NAME = "peerchat_db";

    // Danh sách mật khẩu root thông dụng để thử tự động nếu không có cấu hình
    private static final String[][] CANDIDATE_CREDS = {
            {"root", ""},
            {"root", "root"},
            {"root", "123456"},
            {"root", "admin"},
            {"root", "password"}
    };

    private String dbHost = DEFAULT_HOST;
    private int dbPort = DEFAULT_PORT;
    private String dbUser = "root";
    private String dbPassword = "";

    private Connection connection;
    private volatile boolean available = false;

    public MySqlDataStore() {
        loadConfig();
    }

    public MySqlDataStore(String host, int port, String user, String password) {
        this.dbHost = (host != null) ? host : DEFAULT_HOST;
        this.dbPort = (port > 0) ? port : DEFAULT_PORT;
        this.dbUser = (user != null) ? user : "root";
        this.dbPassword = (password != null) ? password : "";
    }

    private void loadConfig() {
        File configFile = new File("db.properties");
        if (configFile.exists()) {
            try (InputStream is = new FileInputStream(configFile)) {
                Properties props = new Properties();
                props.load(is);
                this.dbHost = props.getProperty("db.host", DEFAULT_HOST);
                this.dbPort = Integer.parseInt(props.getProperty("db.port", String.valueOf(DEFAULT_PORT)));
                this.dbUser = props.getProperty("db.user", "root");
                this.dbPassword = props.getProperty("db.password", "");
                LOGGER.info("[MYSQL] Đã nạp cấu hình từ db.properties (Host: " + dbHost + ":" + dbPort + ", User: " + dbUser + ")");
            } catch (Exception e) {
                LOGGER.warning("[MYSQL] Không thể đọc db.properties: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized boolean init() {
        try {
            // Đảm bảo MySQL JDBC Driver đã nạp
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.severe("[MYSQL_ERR] Không tìm thấy MySQL JDBC Driver: " + e.getMessage());
            available = false;
            return false;
        }

        // Bước 1: Thử kết nối trực tiếp với tài khoản đã cấu hình
        if (tryEstablishConnection(dbUser, dbPassword)) {
            available = setupSchema();
            return available;
        }

        // Bước 2: Thử các tài khoản mặc định phổ biến
        for (String[] cred : CANDIDATE_CREDS) {
            if (cred[0].equals(dbUser) && cred[1].equals(dbPassword)) continue;
            LOGGER.info("[MYSQL] Đang thử kết nối MySQL với user: '" + cred[0] + "'...");
            if (tryEstablishConnection(cred[0], cred[1])) {
                this.dbUser = cred[0];
                this.dbPassword = cred[1];
                LOGGER.info("[MYSQL] Kết nối thành công bằng user: '" + cred[0] + "'!");
                available = setupSchema();
                return available;
            }
        }

        LOGGER.warning("[MYSQL_UNAVAILABLE] Không thể kết nối đến MySQL trên " + dbHost + ":" + dbPort);
        available = false;
        return false;
    }

    private boolean tryEstablishConnection(String user, String pass) {
        // Chuỗi kết nối ban đầu chưa gắn db để tạo database nếu chưa có
        String serverUrl = "jdbc:mysql://" + dbHost + ":" + dbPort +
                "/?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&connectTimeout=3000&socketTimeout=5000&allowPublicKeyRetrieval=true&useSSL=false";
        try (Connection testConn = DriverManager.getConnection(serverUrl, user, pass);
             Statement stmt = testConn.createStatement()) {

            // Tạo database với bảng mã utf8mb4 hỗ trợ trọn vẹn Emoji 4-bytes
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

            String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + DB_NAME +
                    "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&connectTimeout=3000&socketTimeout=5000&allowPublicKeyRetrieval=true&useSSL=false";
            this.connection = DriverManager.getConnection(dbUrl, user, pass);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // Khởi tạo các bảng nếu chưa tồn tại
    private boolean setupSchema() {
        if (connection == null) return false;
        try (Statement stmt = connection.createStatement()) {

            // Bảng người dùng
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id VARCHAR(64) PRIMARY KEY,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    display_name VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);

            // Bảng nhóm Multicast
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS chat_groups (
                    group_id VARCHAR(64) PRIMARY KEY,
                    group_name VARCHAR(100) NOT NULL,
                    creator_id VARCHAR(64) NOT NULL,
                    is_public BOOLEAN DEFAULT FALSE,
                    created_at BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);

            try {
                stmt.executeUpdate("ALTER TABLE chat_groups ADD COLUMN is_public BOOLEAN DEFAULT FALSE");
            } catch (Exception ignored) {}

            // Bảng thành viên nhóm
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS group_members (
                    group_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (group_id, user_id),
                    CONSTRAINT fk_group FOREIGN KEY (group_id) REFERENCES chat_groups(group_id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);

            // Bảng yêu cầu xin vào nhóm kín
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS group_join_requests (
                    group_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    created_at BIGINT NOT NULL,
                    PRIMARY KEY (group_id, user_id),
                    CONSTRAINT fk_req_group FOREIGN KEY (group_id) REFERENCES chat_groups(group_id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);

            // Bảng tập tin đã chia sẻ
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS shared_files (
                    file_id VARCHAR(64) PRIMARY KEY,
                    file_name VARCHAR(255) NOT NULL,
                    file_size BIGINT NOT NULL,
                    checksum VARCHAR(64) NOT NULL,
                    sender_id VARCHAR(64) NOT NULL,
                    sender_name VARCHAR(100) NOT NULL,
                    target_id VARCHAR(64) NOT NULL,
                    stored_path VARCHAR(500) NOT NULL,
                    total_chunks INT NOT NULL DEFAULT 1,
                    chunk_size INT NOT NULL DEFAULT 65536,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);

            // Bảng tin nhắn trò chuyện (Lưu trữ cả Text, File, Voice Note Audio, Video)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS messages (
                    id VARCHAR(64) PRIMARY KEY,
                    sender_id VARCHAR(64) NOT NULL,
                    sender_name VARCHAR(100) NOT NULL,
                    target_id VARCHAR(64) NOT NULL,
                    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
                    content TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                    file_id VARCHAR(64) DEFAULT NULL,
                    timestamp BIGINT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_target (target_id),
                    INDEX idx_timestamp (timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);

            LOGGER.info("[MYSQL_SCHEMA_OK] Toàn bộ bảng CSDL đã được kiểm tra và sẵn sàng.");
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[MYSQL_SCHEMA_FAIL] Lỗi tạo bảng: " + e.getMessage(), e);
            return false;
        }
    }

    private synchronized Connection getValidConnection() {
        try {
            if (connection != null && !connection.isClosed() && connection.isValid(2)) {
                return connection;
            }
            String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + DB_NAME +
                    "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&connectTimeout=3000&socketTimeout=5000&allowPublicKeyRetrieval=true&useSSL=false";
            this.connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            return connection;
        } catch (Exception e) {
            LOGGER.warning("[MYSQL_RECONNECT_FAIL] Không thể tái kết nối MySQL: " + e.getMessage());
            available = false;
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getStoreType() {
        return "MYSQL";
    }

    @Override
    public void saveMessage(Message msg) {
        if (!available || msg == null) return;
        String sql = "INSERT INTO messages (id, sender_id, sender_name, target_id, message_type, content, file_id, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE content = VALUES(content)";
        Connection conn = getValidConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getId());
            ps.setString(2, msg.getSenderId());
            ps.setString(3, msg.getSenderName());
            ps.setString(4, msg.getTargetId());
            ps.setString(5, msg.getMessageType() != null ? msg.getMessageType() : "TEXT");
            ps.setString(6, msg.getContent());
            ps.setString(7, msg.getFileInfo() != null ? msg.getFileInfo().getFileId() : null);
            ps.setLong(8, msg.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_SAVE_MSG_ERR] " + e.getMessage());
        }
    }

    @Override
    public List<Message> getRecentMessages(int limit) {
        List<Message> list = new ArrayList<>();
        if (!available) return list;

        String sql = "SELECT m.id, m.sender_id, m.sender_name, m.target_id, m.message_type, m.content, m.file_id, m.timestamp, " +
                "f.file_name, f.file_size, f.checksum, f.total_chunks, f.chunk_size " +
                "FROM messages m " +
                "LEFT JOIN shared_files f ON m.file_id = f.file_id " +
                "WHERE m.target_id = 'ALL' " +
                "ORDER BY m.timestamp DESC LIMIT ?";

        Connection conn = getValidConnection();
        if (conn == null) return list;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Message m = new Message();
                    m.setId(rs.getString("id"));
                    m.setSenderId(rs.getString("sender_id"));
                    m.setSenderName(rs.getString("sender_name"));
                    m.setTargetId(rs.getString("target_id"));
                    m.setMessageType(rs.getString("message_type"));
                    m.setContent(rs.getString("content"));
                    m.setTimestamp(rs.getLong("timestamp"));

                    String fileId = rs.getString("file_id");
                    if (fileId != null && !fileId.isEmpty()) {
                        FileInfo fi = new FileInfo();
                        fi.setFileId(fileId);
                        fi.setFileName(rs.getString("file_name"));
                        fi.setFileSize(rs.getLong("file_size"));
                        fi.setChecksum(rs.getString("checksum"));
                        fi.setSenderId(m.getSenderId());
                        fi.setSenderName(m.getSenderName());
                        fi.setTargetId(m.getTargetId());
                        fi.setTotalChunks(rs.getInt("total_chunks"));
                        fi.setChunkSize(rs.getInt("chunk_size"));
                        m.setFileInfo(fi);
                    }
                    list.add(m);
                }
            }
            // Đảo ngược lại để theo thứ tự thời gian tăng dần
            Collections.reverse(list);
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_GET_RECENT_ERR] " + e.getMessage());
        }
        return list;
    }

    @Override
    public void saveFile(FileInfo fileInfo, String storedPath) {
        if (!available || fileInfo == null) return;
        String sql = "INSERT INTO shared_files (file_id, file_name, file_size, checksum, sender_id, sender_name, target_id, stored_path, total_chunks, chunk_size) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE file_name=VALUES(file_name), stored_path=VALUES(stored_path)";
        Connection conn = getValidConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileInfo.getFileId());
            ps.setString(2, fileInfo.getFileName());
            ps.setLong(3, fileInfo.getFileSize());
            ps.setString(4, fileInfo.getChecksum());
            ps.setString(5, fileInfo.getSenderId());
            ps.setString(6, fileInfo.getSenderName());
            ps.setString(7, fileInfo.getTargetId());
            ps.setString(8, (storedPath != null) ? storedPath : "");
            ps.setInt(9, fileInfo.getTotalChunks());
            ps.setInt(10, fileInfo.getChunkSize());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_SAVE_FILE_ERR] " + e.getMessage());
        }
    }

    @Override
    public FileInfo getFile(String fileId) {
        if (!available || fileId == null) return null;
        String sql = "SELECT * FROM shared_files WHERE file_id = ?";
        Connection conn = getValidConnection();
        if (conn == null) return null;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    FileInfo info = new FileInfo();
                    info.setFileId(rs.getString("file_id"));
                    info.setFileName(rs.getString("file_name"));
                    info.setFileSize(rs.getLong("file_size"));
                    info.setChecksum(rs.getString("checksum"));
                    info.setSenderId(rs.getString("sender_id"));
                    info.setSenderName(rs.getString("sender_name"));
                    info.setTargetId(rs.getString("target_id"));
                    info.setTotalChunks(rs.getInt("total_chunks"));
                    info.setChunkSize(rs.getInt("chunk_size"));
                    return info;
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_GET_FILE_ERR] " + e.getMessage());
        }
        return null;
    }

    @Override
    public void saveGroup(GroupInfo group) {
        if (!available || group == null) return;
        String sql = "INSERT INTO chat_groups (group_id, group_name, creator_id, is_public, created_at) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE group_name=VALUES(group_name), creator_id=VALUES(creator_id), is_public=VALUES(is_public)";
        Connection conn = getValidConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group.getGroupId());
            ps.setString(2, group.getGroupName());
            ps.setString(3, group.getCreatorId());
            ps.setBoolean(4, group.isPublic());
            ps.setLong(5, group.getCreatedAt());
            ps.executeUpdate();

            // Lưu các thành viên
            for (String memberId : group.getMemberIds()) {
                addGroupMember(group.getGroupId(), memberId);
            }
            // Lưu các yêu cầu đang chờ
            for (String pendingId : group.getPendingMemberIds()) {
                addJoinRequest(group.getGroupId(), pendingId);
            }
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_SAVE_GROUP_ERR] " + e.getMessage());
        }
    }

    @Override
    public void addGroupMember(String groupId, String userId) {
        if (!available || groupId == null || userId == null) return;
        String sql = "INSERT IGNORE INTO group_members (group_id, user_id) VALUES (?, ?)";
        Connection conn = getValidConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_ADD_MEMBER_ERR] " + e.getMessage());
        }
    }

    @Override
    public void removeGroupMember(String groupId, String userId) {
        if (!available || groupId == null || userId == null) return;
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        Connection conn = getValidConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_REMOVE_MEMBER_ERR] " + e.getMessage());
        }
    }

    @Override
    public void deleteGroup(String groupId) {
        if (!available || groupId == null) return;
        String sql = "DELETE FROM chat_groups WHERE group_id = ?";
        Connection conn = getValidConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_DELETE_GROUP_ERR] " + e.getMessage());
        }
    }

    @Override
    public List<GroupInfo> getAllGroups() {
        List<GroupInfo> list = new ArrayList<>();
        if (!available) return list;

        String sqlGroup = "SELECT * FROM chat_groups";
        String sqlMembers = "SELECT user_id FROM group_members WHERE group_id = ?";
        String sqlRequests = "SELECT user_id FROM group_join_requests WHERE group_id = ?";
        Connection conn = getValidConnection();
        if (conn == null) return list;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlGroup)) {

            while (rs.next()) {
                String groupId = rs.getString("group_id");
                String groupName = rs.getString("group_name");
                String creatorId = rs.getString("creator_id");
                boolean isPublic = rs.getBoolean("is_public");
                long createdAt = rs.getLong("created_at");

                GroupInfo group = new GroupInfo(groupId, groupName, creatorId, isPublic);
                group.setCreatedAt(createdAt);

                try (PreparedStatement psMem = conn.prepareStatement(sqlMembers)) {
                    psMem.setString(1, groupId);
                    try (ResultSet rsMem = psMem.executeQuery()) {
                        while (rsMem.next()) {
                            group.addMember(rsMem.getString("user_id"));
                        }
                    }
                }

                try (PreparedStatement psReq = conn.prepareStatement(sqlRequests)) {
                    psReq.setString(1, groupId);
                    try (ResultSet rsReq = psReq.executeQuery()) {
                        while (rsReq.next()) {
                            group.addJoinRequest(rsReq.getString("user_id"));
                        }
                    }
                }

                list.add(group);
            }
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_GET_GROUPS_ERR] " + e.getMessage());
        }
        return list;
    }

    @Override
    public void addJoinRequest(String groupId, String userId) {
        if (!available || groupId == null || userId == null) return;
        String sql = "INSERT IGNORE INTO group_join_requests (group_id, user_id, created_at) VALUES (?, ?, ?)";
        Connection conn = getValidConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, userId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_ADD_JOIN_REQ_ERR] " + e.getMessage());
        }
    }

    @Override
    public void removeJoinRequest(String groupId, String userId) {
        if (!available || groupId == null || userId == null) return;
        String sql = "DELETE FROM group_join_requests WHERE group_id = ? AND user_id = ?";
        Connection conn = getValidConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_REMOVE_JOIN_REQ_ERR] " + e.getMessage());
        }
    }

    @Override
    public void updateGroupPublicStatus(String groupId, boolean isPublic) {
        if (!available || groupId == null) return;
        String sql = "UPDATE chat_groups SET is_public = ? WHERE group_id = ?";
        Connection conn = getValidConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isPublic);
            ps.setString(2, groupId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("[MYSQL_UPDATE_GROUP_PUBLIC_ERR] " + e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
        available = false;
    }
}
