package com.peerchat.client.service;

import com.peerchat.client.model.TransferState;
import com.peerchat.client.network.MessageSender;
import com.peerchat.client.util.ClientUtils;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolConstants;
import com.peerchat.shared.protocol.ProtocolMessage;
import com.peerchat.shared.util.ChecksumUtils;
import com.peerchat.shared.util.FileUtils;
import javafx.beans.property.*;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Dịch vụ điều phối gửi và nhận file phía Client (chia chunk 64KB, kiểm tra SHA-256)
// Dịch vụ điều phối gửi và nhận file phía Client theo mô hình Store-and-Forward
public class FileTransferService {
    // Callback thông báo tiến trình và kết quả tải file
    public interface DownloadCallback {
        void onProgress(double progress, long currentBytes, long totalBytes);
        void onComplete(boolean success, String hash, String error);
    }

    // Phiên tải file đang hoạt động
    private static class DownloadSession {
        final FileInfo info;
        final File destFile;
        final FileOutputStream out;
        final DownloadCallback callback;
        long bytesReceived = 0;

        DownloadSession(FileInfo info, File destFile, FileOutputStream out, DownloadCallback callback) {
            this.info = info;
            this.destFile = destFile;
            this.out = out;
            this.callback = callback;
        }
    }

    private final MessageSender sender;
    private final String clientId;
    private final String displayName;
    private final ExecutorService transferPool = Executors.newCachedThreadPool();

    // Thuộc tính quan sát hiển thị trên thanh trạng thái và telemetry panel
    private final ObjectProperty<TransferState> state = new SimpleObjectProperty<>(TransferState.IDLE);
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private final StringProperty statusText = new SimpleStringProperty("SẴN SÀNG");
    private final StringProperty activeFileName = new SimpleStringProperty("");
    private final StringProperty telemetrySpeed = new SimpleStringProperty("0.0 KB/s");
    private final StringProperty checksumResult = new SimpleStringProperty("");

    private final java.util.Map<String, DownloadSession> activeDownloads = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicBoolean transferAborted = new AtomicBoolean(false);

    public FileTransferService(MessageSender sender, String clientId, String displayName) {
        this.sender = sender;
        this.clientId = clientId;
        this.displayName = displayName;
    }

    public ObjectProperty<TransferState> stateProperty() { return state; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty statusTextProperty() { return statusText; }
    public StringProperty activeFileNameProperty() { return activeFileName; }
    public StringProperty telemetrySpeedProperty() { return telemetrySpeed; }
    public StringProperty checksumResultProperty() { return checksumResult; }

    // Upload file lên Server: tính SHA-256, gửi metadata, stream chunk và báo hoàn tất
    public void stageAndSendFile(File file, String targetClientId) {
        if (file == null || !file.exists() || !file.isFile()) {
            updateStatus(TransferState.FAILED, "LỖI: Tập tin đã chọn không hợp lệ", 0.0);
            return;
        }

        transferAborted.set(false);
        updateStatus(TransferState.REQUESTING, "ĐANG TÍNH MÃ BĂM SHA-256...", 0.0);
        activeFileName.set(file.getName() + " (" + FileUtils.formatFileSize(file.length()) + ")");
        checksumResult.set("");

        transferPool.submit(() -> {
            try {
                String checksum = ChecksumUtils.calculateSHA256(file);
                long fileSize = file.length();
                int totalChunks = (int) Math.ceil((double) fileSize / ProtocolConstants.CHUNK_SIZE);
                if (totalChunks == 0) totalChunks = 1;

                String target = (targetClientId != null && !targetClientId.isEmpty()) ? targetClientId : ProtocolConstants.TARGET_ALL;

                FileInfo sendingInfo = new FileInfo(
                        file.getName(),
                        fileSize,
                        checksum,
                        clientId,
                        displayName,
                        target,
                        totalChunks,
                        ProtocolConstants.CHUNK_SIZE
                );

                updateStatus(TransferState.TRANSFERRING, "ĐANG TẢI LÊN MÁY CHỦ...", 0.0);

                // Gửi metadata lên server
                ProtocolMessage metaMsg = ProtocolMessage.createText(MessageType.FILE_METADATA, sendingInfo.toJson());
                sender.sendSync(metaMsg);

                // Đọc file và stream chunk lên server
                try (InputStream fis = new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buffer = new byte[ProtocolConstants.CHUNK_SIZE];
                    int bytesRead;
                    int chunkIndex = 0;
                    long totalBytesSent = 0;
                    long lastSpeedTime = System.currentTimeMillis();
                    long bytesSinceLastSpeed = 0;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        if (transferAborted.get()) {
                            updateStatus(TransferState.FAILED, "ĐÃ HỦY TẢI LÊN", 0.0);
                            return;
                        }

                        byte[] chunkPayload = (bytesRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                        ProtocolMessage chunkMsg = ProtocolMessage.createFileData(sendingInfo.getFileId(), chunkIndex, chunkPayload);
                        sender.sendSync(chunkMsg);

                        chunkIndex++;
                        totalBytesSent += bytesRead;
                        bytesSinceLastSpeed += bytesRead;

                        double currentProgress = fileSize > 0 ? (double) totalBytesSent / fileSize : 1.0;

                        long now = System.currentTimeMillis();
                        if (now - lastSpeedTime >= 300) {
                            double elapsedSec = (now - lastSpeedTime) / 1000.0;
                            double speedBps = bytesSinceLastSpeed / elapsedSec;
                            String speedText = FileUtils.formatTransferSpeed(speedBps);
                            ClientUtils.runOnFxThread(() -> telemetrySpeed.set(speedText));
                            lastSpeedTime = now;
                            bytesSinceLastSpeed = 0;
                        }

                        final double prog = currentProgress;
                        final String stat = String.format("Đã gửi: %d/%d chunk (%.1f%%)", chunkIndex, totalChunks, prog * 100.0);
                        ClientUtils.runOnFxThread(() -> {
                            progress.set(prog);
                            statusText.set(stat);
                        });
                    }

                    // Gửi thông báo hoàn tất upload
                    ProtocolMessage completeMsg = ProtocolMessage.createText(MessageType.FILE_COMPLETE, sendingInfo.toJson());
                    sender.sendSync(completeMsg);

                    updateStatus(TransferState.VERIFYING, "ĐÃ TẢI LÊN SERVER. CHỜ XÁC THỰC MÃ BĂM...", 1.0);
                }

            } catch (IOException e) {
                updateStatus(TransferState.FAILED, "LỖI TẢI LÊN: " + e.getMessage(), 0.0);
            }
        });
    }

    // Yêu cầu tải file từ Server về máy và lưu vào đích đã chọn
    public void requestDownload(FileInfo fileInfo, File destinationFile, DownloadCallback callback) {
        if (fileInfo == null || destinationFile == null) return;

        try {
            FileOutputStream fos = new FileOutputStream(destinationFile);
            DownloadSession session = new DownloadSession(fileInfo, destinationFile, fos, callback);
            activeDownloads.put(fileInfo.getFileId(), session);

            activeFileName.set(fileInfo.getFileName() + " (" + FileUtils.formatFileSize(fileInfo.getFileSize()) + ")");
            updateStatus(TransferState.TRANSFERRING, "ĐANG YÊU CẦU TẢI TỪ MÁY CHỦ...", 0.0);

            // Gửi FILE_DOWNLOAD_REQ lên server
            ProtocolMessage reqMsg = ProtocolMessage.createText(MessageType.FILE_DOWNLOAD_REQ, "{\"fileId\":\"" + fileInfo.getFileId() + "\"}");
            sender.sendAsync(reqMsg);

        } catch (IOException e) {
            if (callback != null) {
                callback.onComplete(false, "", "Không thể tạo file lưu: " + e.getMessage());
            }
            updateStatus(TransferState.FAILED, "LỖI LƯU FILE: " + e.getMessage(), 0.0);
        }
    }

    // Tiếp nhận chunk dữ liệu tải về từ Server
    public void onIncomingFileData(ProtocolMessage.FileChunk chunk) {
        DownloadSession session = activeDownloads.get(chunk.fileId());
        if (session == null) return;

        try {
            session.out.write(chunk.data());
            session.bytesReceived += chunk.data().length;

            long total = session.info.getFileSize();
            double currentProgress = total > 0 ? (double) session.bytesReceived / total : 1.0;
            final double prog = Math.min(1.0, currentProgress);

            if (session.callback != null) {
                ClientUtils.runOnFxThread(() -> session.callback.onProgress(prog, session.bytesReceived, total));
            }

            final String stat = String.format("Đang tải: %s / %s (%.1f%%)",
                    FileUtils.formatFileSize(session.bytesReceived),
                    FileUtils.formatFileSize(total),
                    prog * 100.0);

            ClientUtils.runOnFxThread(() -> {
                progress.set(prog);
                statusText.set(stat);
            });

        } catch (IOException e) {
            updateStatus(TransferState.FAILED, "LỖI GHI DỮ LIỆU: " + e.getMessage(), 0.0);
        }
    }

    // Hoàn tất tải file: Đóng file, tính lại SHA-256 và đối soát
    public void onIncomingFileComplete(FileInfo info) {
        DownloadSession session = activeDownloads.remove(info.getFileId());
        if (session == null) return;

        try {
            session.out.flush();
            session.out.close();
        } catch (IOException ignored) {}

        updateStatus(TransferState.VERIFYING, "ĐANG KIỂM TRA MÃ SHA-256...", 1.0);

        transferPool.submit(() -> {
            try {
                String localHash = ChecksumUtils.calculateSHA256(session.destFile);
                String expected = session.info.getChecksum();
                boolean match = (expected == null || expected.isEmpty()) || ChecksumUtils.verifyChecksum(expected, localHash);

                ClientUtils.runOnFxThread(() -> {
                    if (match) {
                        state.set(TransferState.COMPLETED);
                        statusText.set("ĐÃ TẢI XONG // SHA-256 TOÀN VẸN 100%");
                        checksumResult.set("THÀNH CÔNG: " + localHash.substring(0, Math.min(16, localHash.length())) + "...");
                        if (session.callback != null) {
                            session.callback.onComplete(true, localHash, null);
                        }
                    } else {
                        state.set(TransferState.FAILED);
                        statusText.set("DỮ LIỆU BỊ LỖI // SHA-256 KHÔNG KHỚP");
                        checksumResult.set("LỖI MÃ BĂM");
                        if (session.callback != null) {
                            session.callback.onComplete(false, localHash, "SHA-256 không khớp");
                        }
                    }
                });

            } catch (IOException e) {
                updateStatus(TransferState.FAILED, "LỖI TÍNH SHA-256: " + e.getMessage(), 0.0);
                if (session.callback != null) {
                    ClientUtils.runOnFxThread(() -> session.callback.onComplete(false, "", e.getMessage()));
                }
            }
        });
    }

    // Server phản hồi trạng thái xác thực file upload
    public void onFileStatusReceived(FileInfo info) {
        ClientUtils.runOnFxThread(() -> {
            if ("VERIFIED".equalsIgnoreCase(info.getChecksum())) {
                state.set(TransferState.COMPLETED);
                statusText.set("MÁY CHỦ ĐÃ LƯU TRỮ // ĐÃ PHÁT VÀO PHÒNG CHAT");
                checksumResult.set("XÁC THỰC MÃ BĂM: THÀNH CÔNG");
            } else {
                state.set(TransferState.FAILED);
                statusText.set("MÁY CHỦ BÁO CÁO FILE BỊ LỖI MÃ BĂM");
                checksumResult.set("XÁC THỰC MÃ BĂM: THẤT BẠI");
            }
        });
    }

    // Hủy bỏ quá trình truyền tải
    public void abortTransfer() {
        transferAborted.set(true);
        for (DownloadSession s : activeDownloads.values()) {
            try { s.out.close(); } catch (IOException ignored) {}
        }
        activeDownloads.clear();
        updateStatus(TransferState.IDLE, "ĐÃ HỦY TRUYỀN", 0.0);
    }

    private void updateStatus(TransferState newState, String text, double prog) {
        ClientUtils.runOnFxThread(() -> {
            state.set(newState);
            statusText.set(text);
            progress.set(prog);
        });
    }
}
