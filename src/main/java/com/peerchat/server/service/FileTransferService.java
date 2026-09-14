package com.peerchat.server.service;

import com.peerchat.server.network.ConnectionManager;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolMessage;

import com.peerchat.shared.model.Message;
import com.peerchat.shared.protocol.ProtocolConstants;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

// Dịch vụ lưu trữ và truyền file Store-and-Forward phía Server
public class FileTransferService {
    private static final Logger LOGGER = Logger.getLogger(FileTransferService.class.getName());
    private static final Path STORAGE_DIR = Paths.get("server_storage");

    private final ConnectionManager connectionManager;
    private final ChatService chatService;
    private final ExecutorService downloadPool = Executors.newCachedThreadPool();

    // Quản lý phiên upload đang diễn ra
    private final Map<String, UploadSession> activeUploads = new ConcurrentHashMap<>();
    // Danh mục các file đã lưu trữ thành công trên server
    private final Map<String, FileInfo> storedFiles = new ConcurrentHashMap<>();

    // Phiên upload file tạm thời
    private static class UploadSession {
        final FileInfo info;
        final File targetFile;
        final FileOutputStream outputStream;
        final java.security.MessageDigest digest;
        long bytesReceived = 0;

        UploadSession(FileInfo info, File targetFile, FileOutputStream outputStream, java.security.MessageDigest digest) {
            this.info = info;
            this.targetFile = targetFile;
            this.outputStream = outputStream;
            this.digest = digest;
        }
    }

    public FileTransferService(ConnectionManager connectionManager, ChatService chatService) {
        this.connectionManager = connectionManager;
        this.chatService = chatService;
        ensureStorageDir();
    }

    // Đảm bảo thư mục server_storage luôn tồn tại
    private void ensureStorageDir() {
        try {
            if (!Files.exists(STORAGE_DIR)) {
                Files.createDirectories(STORAGE_DIR);
            }
        } catch (IOException e) {
            LOGGER.warning("[STORAGE_DIR] Cannot create server_storage directory: " + e.getMessage());
        }
    }

    // Khởi tạo phiên upload file từ Client lên Server
    public void handleFileUploadMetadata(FileInfo info) {
        ensureStorageDir();
        try {
            String safeName = info.getFileName().replaceAll("[^a-zA-Z0-9._-]", "_");
            File targetFile = STORAGE_DIR.resolve(info.getFileId() + "_" + safeName).toFile();
            FileOutputStream fos = new FileOutputStream(targetFile);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");

            UploadSession session = new UploadSession(info, targetFile, fos, md);
            activeUploads.put(info.getFileId(), session);

            LOGGER.info("[UPLOAD_START] " + info.getFileName() + " (" + info.getFileSize() + " B) from "
                    + info.getSenderName() + " [" + info.getSenderId() + "]");
        } catch (Exception e) {
            LOGGER.warning("[UPLOAD_INIT_FAIL] " + e.getMessage());
        }
    }

    // Tiếp nhận và ghi từng chunk nhị phân vào file lưu trữ trên Server
    public void handleFileUploadData(ProtocolMessage chunkMsg, String senderId) {
        try {
            ProtocolMessage.FileChunk chunk = chunkMsg.parseFileData();
            UploadSession session = activeUploads.get(chunk.fileId());
            if (session != null) {
                session.outputStream.write(chunk.data());
                session.digest.update(chunk.data());
                session.bytesReceived += chunk.data().length;
            }
        } catch (Exception e) {
            LOGGER.warning("[UPLOAD_CHUNK_FAIL] " + e.getMessage());
        }
    }

    // Hoàn tất upload: Xác thực mã băm SHA-256 và tạo tin nhắn file vào khung chat
    public void handleFileUploadComplete(FileInfo info) {
        UploadSession session = activeUploads.remove(info.getFileId());
        if (session == null) return;

        try {
            session.outputStream.flush();
            session.outputStream.close();

            byte[] hashBytes = session.digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String serverHash = sb.toString();

            boolean isMatch = serverHash.equalsIgnoreCase(info.getChecksum());

            if (isMatch) {
                LOGGER.info("[UPLOAD_VERIFIED] SHA-256 MATCH for " + info.getFileName() + " (" + serverHash.substring(0, 8) + "...)");
                storedFiles.put(info.getFileId(), info);

                // Tạo tin nhắn đính kèm file và gửi vào luồng chat phòng
                Message fileMsg = new Message(info.getSenderId(), info.getSenderName(), info.getTargetId(), info);
                chatService.processAndRoute(fileMsg);

                // Phản hồi trạng thái xác thực thành công cho người gửi
                FileInfo statusReport = new FileInfo();
                statusReport.setFileId(info.getFileId());
                statusReport.setChecksum("VERIFIED");
                ProtocolMessage statusMsg = ProtocolMessage.createText(MessageType.FILE_STATUS, statusReport.toJson());
                connectionManager.routeMessage(info.getSenderId(), statusMsg, null);

            } else {
                LOGGER.warning("[UPLOAD_CORRUPTED] SHA-256 mismatch for " + info.getFileName() + " (server=" + serverHash + ", client=" + info.getChecksum() + ")");
                session.targetFile.delete();

                FileInfo statusReport = new FileInfo();
                statusReport.setFileId(info.getFileId());
                statusReport.setChecksum("CORRUPT");
                ProtocolMessage statusMsg = ProtocolMessage.createText(MessageType.FILE_STATUS, statusReport.toJson());
                connectionManager.routeMessage(info.getSenderId(), statusMsg, null);
            }

        } catch (Exception e) {
            LOGGER.warning("[UPLOAD_FINISH_FAIL] " + e.getMessage());
        }
    }

    // Xử lý yêu cầu tải file từ một Client
    public void handleFileDownloadRequest(String fileId, String requestingClientId) {
        downloadPool.submit(() -> {
            try {
                FileInfo info = storedFiles.get(fileId);
                File targetFile = null;

                if (info != null) {
                    String safeName = info.getFileName().replaceAll("[^a-zA-Z0-9._-]", "_");
                    targetFile = STORAGE_DIR.resolve(fileId + "_" + safeName).toFile();
                }

                if (targetFile == null || !targetFile.exists()) {
                    File[] matches = STORAGE_DIR.toFile().listFiles((dir, name) -> name.startsWith(fileId));
                    if (matches != null && matches.length > 0) {
                        targetFile = matches[0];
                    }
                }

                if (targetFile == null || !targetFile.exists()) {
                    LOGGER.warning("[DOWNLOAD_NOT_FOUND] Requested fileId not found: " + fileId);
                    FileInfo notFound = new FileInfo();
                    notFound.setFileId(fileId);
                    notFound.setChecksum("NOT_FOUND");
                    ProtocolMessage errPacket = ProtocolMessage.createText(MessageType.FILE_STATUS, notFound.toJson());
                    connectionManager.routeMessage(requestingClientId, errPacket, null);
                    return;
                }

                LOGGER.info("[DOWNLOAD_START] Streaming " + targetFile.getName() + " to client [" + requestingClientId + "]");

                if (info == null) {
                    String cleanName = targetFile.getName().substring(targetFile.getName().indexOf('_') + 1);
                    int totalChunks = (int) Math.ceil((double) targetFile.length() / ProtocolConstants.CHUNK_SIZE);
                    info = new FileInfo(cleanName, targetFile.length(), "", "SERVER", "STORE", requestingClientId, totalChunks, ProtocolConstants.CHUNK_SIZE);
                    info.setFileId(fileId);
                }

                // Gửi FILE_METADATA cho client tải
                ProtocolMessage metaMsg = ProtocolMessage.createText(MessageType.FILE_METADATA, info.toJson());
                connectionManager.routeMessage(requestingClientId, metaMsg, null);

                // Stream từng chunk 64KB đến client
                try (InputStream fis = new BufferedInputStream(new FileInputStream(targetFile))) {
                    byte[] buffer = new byte[ProtocolConstants.CHUNK_SIZE];
                    int bytesRead;
                    int chunkIndex = 0;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] payload = (bytesRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                        ProtocolMessage chunkMsg = ProtocolMessage.createFileData(fileId, chunkIndex, payload);
                        connectionManager.routeMessage(requestingClientId, chunkMsg, null);
                        chunkIndex++;
                    }
                }

                // Gửi thông báo hoàn tất tải file
                ProtocolMessage completeMsg = ProtocolMessage.createText(MessageType.FILE_DOWNLOAD_COMPLETE, info.toJson());
                connectionManager.routeMessage(requestingClientId, completeMsg, null);

                LOGGER.info("[DOWNLOAD_SUCCESS] Completed sending " + info.getFileName() + " to client [" + requestingClientId + "]");

            } catch (Exception e) {
                LOGGER.warning("[DOWNLOAD_FAIL] Error streaming file: " + e.getMessage());
            }
        });
    }

    public FileInfo getStoredFileInfo(String fileId) {
        return storedFiles.get(fileId);
    }
}
