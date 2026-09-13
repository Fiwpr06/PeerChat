package com.peerchat.client.model;

// Các trạng thái kết nối mạng của Client
public enum ClientState {
    DISCONNECTED,       // Chưa kết nối
    CONNECTING,         // Đang kết nối
    CONNECTED,          // Đã kết nối thành công
    CONNECTION_FAILED   // Kết nối thất bại
}
