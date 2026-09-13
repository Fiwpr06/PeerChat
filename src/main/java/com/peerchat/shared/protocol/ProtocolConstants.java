package com.peerchat.shared.protocol;

// Các hằng số cấu hình toàn bộ hệ thống mạng
public final class ProtocolConstants {
    private ProtocolConstants() {}

    // Cổng mạng mặc định của Server
    public static final int DEFAULT_PORT = 5000;

    // Độ dài mã kết nối ngẫu nhiên của Server (5 ký tự)
    public static final int CONNECTION_CODE_LENGTH = 5;

    // Kích thước mỗi chunk khi chia nhỏ file (64 KB)
    public static final int CHUNK_SIZE = 64 * 1024;

    // Giới hạn kích thước tối đa của một khung gói tin (16 MB)
    public static final int MAX_FRAME_LENGTH = 16 * 1024 * 1024;

    // Thời gian chờ kết nối tối đa (15 giây)
    public static final int SOCKET_TIMEOUT_MS = 15000;

    // Định danh gửi tin nhắn tới tất cả client
    public static final String TARGET_ALL = "ALL";
}
