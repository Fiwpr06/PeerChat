package com.peerchat.shared.protocol;

// Danh sách các loại gói tin giao tiếp giữa Client và Server
public enum MessageType {
    // Kết nối và ngắt kết nối
    CONNECT_REQUEST,
    CONNECT_RESPONSE,
    DISCONNECT,

    // Cập nhật danh sách client đang online
    CLIENT_LIST_UPDATE,

    // Tin nhắn chat văn bản
    CHAT_MESSAGE,
    CHAT_BROADCAST,

    // Đàm phán và truyền file theo chunk
    FILE_REQUEST,
    FILE_ACCEPT,
    FILE_REJECT,
    FILE_METADATA,
    FILE_DATA,
    FILE_COMPLETE,
    FILE_STATUS,

    // Kiểm tra kết nối và lỗi
    PING,
    PONG,
    ERROR
}
