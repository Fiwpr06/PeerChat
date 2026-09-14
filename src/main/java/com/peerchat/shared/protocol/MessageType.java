package com.peerchat.shared.protocol;

// Danh sách các loại gói tin giao tiếp giữa Client và Server
public enum MessageType {
    // Kết nối và ngắt kết nối
    CONNECT_REQUEST,
    CONNECT_RESPONSE,
    DISCONNECT,

    // Cập nhật danh sách client đang online
    CLIENT_LIST_UPDATE,

    // Tin nhắn chat văn bản và lịch sử
    CHAT_MESSAGE,
    CHAT_BROADCAST,
    CHAT_HISTORY,

    // Truyền file theo chunk và tải file từ server
    FILE_REQUEST,
    FILE_ACCEPT,
    FILE_REJECT,
    FILE_METADATA,
    FILE_DATA,
    FILE_COMPLETE,
    FILE_STATUS,
    FILE_DOWNLOAD_REQ,
    FILE_DOWNLOAD_COMPLETE,

    // Kiểm tra kết nối và lỗi
    PING,
    PONG,
    ERROR
}
