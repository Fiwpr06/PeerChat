package com.peerchat.server.service;

import com.peerchat.server.network.ConnectionManager;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

// Dịch vụ điều phối truyền file phía Server (chuyển tiếp trực tiếp, không lưu vào đĩa Server)
public class FileTransferService {
    private static final Logger LOGGER = Logger.getLogger(FileTransferService.class.getName());
    private final ConnectionManager connectionManager;

    // Ánh xạ fileId sang ID của Client nhận
    private final Map<String, String> activeTransfers = new ConcurrentHashMap<>();

    public FileTransferService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // Chuyển tiếp yêu cầu gửi file tới người nhận
    public void handleFileRequest(FileInfo info) {
        LOGGER.info("[FILE_REQ] " + info.getFileName() + " (" + info.getFileSize() + " B) từ "
                + info.getSenderName() + " tới " + info.getTargetId());
        activeTransfers.put(info.getFileId(), info.getTargetId());
        ProtocolMessage msg = ProtocolMessage.createText(MessageType.FILE_REQUEST, info.toJson());
        connectionManager.routeMessage(info.getTargetId(), msg, info.getSenderId());
    }

    // Người nhận đồng ý, chuyển tiếp thông báo chấp nhận về người gửi
    public void handleFileAccept(FileInfo info) {
        LOGGER.info("[FILE_ACCEPT] Người nhận " + info.getTargetId() + " đã đồng ý nhận fileId: " + info.getFileId());
        ProtocolMessage msg = ProtocolMessage.createText(MessageType.FILE_ACCEPT, info.toJson());
        connectionManager.routeMessage(info.getSenderId(), msg, info.getTargetId());
    }

    // Người nhận từ chối, chuyển tiếp thông báo từ chối về người gửi
    public void handleFileReject(FileInfo info) {
        LOGGER.info("[FILE_REJECT] Người nhận " + info.getTargetId() + " đã từ chối fileId: " + info.getFileId());
        activeTransfers.remove(info.getFileId());
        ProtocolMessage msg = ProtocolMessage.createText(MessageType.FILE_REJECT, info.toJson());
        connectionManager.routeMessage(info.getSenderId(), msg, info.getTargetId());
    }

    // Chuyển tiếp thông số metadata của file (tổng chunk, checksum)
    public void handleFileMetadata(FileInfo info) {
        LOGGER.info("[FILE_META] fileId: " + info.getFileId() + ", tổng chunk: " + info.getTotalChunks());
        activeTransfers.put(info.getFileId(), info.getTargetId());
        ProtocolMessage msg = ProtocolMessage.createText(MessageType.FILE_METADATA, info.toJson());
        connectionManager.routeMessage(info.getTargetId(), msg, info.getSenderId());
    }

    // Chuyển tiếp tức thì từng chunk dữ liệu nhị phân từ người gửi sang người nhận
    public void relayFileData(ProtocolMessage chunkMsg, String senderId) {
        try {
            ProtocolMessage.FileChunk chunk = chunkMsg.parseFileData();
            String targetId = activeTransfers.get(chunk.fileId());
            if (targetId != null) {
                connectionManager.routeMessage(targetId, chunkMsg, senderId);
            } else {
                LOGGER.warning("Không tìm thấy người nhận cho fileId: " + chunk.fileId());
            }
        } catch (Exception e) {
            LOGGER.warning("Lỗi chuyển tiếp chunk file: " + e.getMessage());
        }
    }

    // Người gửi thông báo đã gửi hết toàn bộ các chunk
    public void handleFileComplete(FileInfo info) {
        LOGGER.info("[FILE_COMPLETE] Đã truyền xong toàn bộ chunk cho fileId: " + info.getFileId());
        ProtocolMessage msg = ProtocolMessage.createText(MessageType.FILE_COMPLETE, info.toJson());
        connectionManager.routeMessage(info.getTargetId(), msg, info.getSenderId());
    }

    // Chuyển tiếp kết quả đối soát SHA-256 từ người nhận về người gửi
    public void handleFileStatus(FileInfo info) {
        LOGGER.info("[FILE_STATUS] Kết quả SHA-256 cho fileId: " + info.getFileId() + " -> " + info.getSenderId());
        activeTransfers.remove(info.getFileId());
        ProtocolMessage msg = ProtocolMessage.createText(MessageType.FILE_STATUS, info.toJson());
        connectionManager.routeMessage(info.getSenderId(), msg, info.getTargetId());
    }
}
