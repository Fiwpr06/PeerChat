package com.peerchat.client.model;

// Các trạng thái của tiến trình truyền hoặc nhận file
public enum TransferState {
    IDLE,            // Trạng thái nghỉ, không truyền nhận
    REQUESTING,      // Đang gửi yêu cầu chuyển file
    WAITING_ACCEPT,  // Đang chờ người nhận đồng ý
    TRANSFERRING,    // Đang truyền dữ liệu theo chunk
    VERIFYING,       // Đang tính toán và đối soát mã SHA-256
    COMPLETED,       // Truyền nhận thành công, hash khớp 100%
    FAILED,          // Quá trình truyền thất bại hoặc sai hash
    REJECTED         // Người nhận đã từ chối nhận file
}
