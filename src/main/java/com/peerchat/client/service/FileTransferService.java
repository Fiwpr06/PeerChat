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
public class FileTransferService {
    // Callback yêu cầu nhận file gửi đến
    public interface FileRequestListener {
        void onIncomingFileRequest(FileInfo info, FileAcceptCallback callback);
    }

    // Callback quyết định đồng ý hoặc từ chối kèm đường dẫn lưu
    public interface FileAcceptCallback {
        void onDecision(boolean accepted, File destinationFile);
    }

    private final MessageSender sender;
    private final String clientId;
    private final String displayName;
    private final ExecutorService transferPool = Executors.newCachedThreadPool();

    // Các thuộc tính quan sát được để gắn kết (bind) với giao diện JavaFX
    private final ObjectProperty<TransferState> state = new SimpleObjectProperty<>(TransferState.IDLE);
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private final StringProperty statusText = new SimpleStringProperty("SẴN SÀNG");
    private final StringProperty activeFileName = new SimpleStringProperty("");
    private final StringProperty telemetrySpeed = new SimpleStringProperty("0.0 KB/s");
    private final StringProperty checksumResult = new SimpleStringProperty("");

    private File stagingFile;
    private FileInfo currentSendingInfo;
    private FileInfo currentReceivingInfo;
    private FileOutputStream receivingOutputStream;
    private File receivingFile;
    private long totalBytesReceived = 0;
    private final AtomicBoolean transferAborted = new AtomicBoolean(false);
    private FileRequestListener incomingRequestListener;

    public FileTransferService(MessageSender sender, String clientId, String displayName) {
        this.sender = sender;
        this.clientId = clientId;
        this.displayName = displayName;
    }

    public void setIncomingRequestListener(FileRequestListener listener) {
        this.incomingRequestListener = listener;
    }

    public ObjectProperty<TransferState> stateProperty() { return state; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty statusTextProperty() { return statusText; }
    public StringProperty activeFileNameProperty() { return activeFileName; }
    public StringProperty telemetrySpeedProperty() { return telemetrySpeed; }
    public StringProperty checksumResultProperty() { return checksumResult; }

    // Bắt đầu quy trình gửi file: Tính trước SHA-256 và gửi FILE_REQUEST tới người nhận
    public void stageAndSendFile(File file, String targetClientId) {
        if (file == null || !file.exists() || !file.isFile()) {
            updateStatus(TransferState.FAILED, "LỖI: File đã chọn không hợp lệ", 0.0);
            return;
        }

        stagingFile = file;
        transferAborted.set(false);
        updateStatus(TransferState.REQUESTING, "ĐANG TÍNH TOÁN SHA-256...", 0.0);
        activeFileName.set(file.getName() + " (" + FileUtils.formatFileSize(file.length()) + ")");
        checksumResult.set("");

        transferPool.submit(() -> {
            try {
                // Tính mã băm SHA-256 trước khi gửi
                String checksum = ChecksumUtils.calculateSHA256(file);
                long fileSize = file.length();
                int totalChunks = (int) Math.ceil((double) fileSize / ProtocolConstants.CHUNK_SIZE);
                if (totalChunks == 0) totalChunks = 1;

                currentSendingInfo = new FileInfo(
                        file.getName(),
                        fileSize,
                        checksum,
                        clientId,
                        displayName,
                        targetClientId,
                        totalChunks,
                        ProtocolConstants.CHUNK_SIZE
                );

                updateStatus(TransferState.WAITING_ACCEPT, "ĐANG CHỜ NGƯỜI NHẬN ĐỒNG Ý...", 0.0);

                // Gửi gói tin yêu cầu truyền file
                ProtocolMessage req = ProtocolMessage.createText(MessageType.FILE_REQUEST, currentSendingInfo.toJson());
                sender.sendSync(req);

            } catch (IOException e) {
                updateStatus(TransferState.FAILED, "LỖI: Không thể tính SHA-256: " + e.getMessage(), 0.0);
            }
        });
    }

    // Người nhận đồng ý: Bắt đầu chia nhỏ file thành chunk 64KB và truyền liên tục
    public void onFileAcceptedByPeer(FileInfo info) {
        if (stagingFile == null || !stagingFile.exists()) {
            updateStatus(TransferState.FAILED, "LỖI: Không tìm thấy file trên đĩa", 0.0);
            return;
        }

        updateStatus(TransferState.TRANSFERRING, "ĐANG TRUYỀN DỮ LIỆU...", 0.0);

        transferPool.submit(() -> {
            try {
                // Gửi thông số metadata của file
                ProtocolMessage metaMsg = ProtocolMessage.createText(MessageType.FILE_METADATA, currentSendingInfo.toJson());
                sender.sendSync(metaMsg);

                // Đọc file và gửi từng chunk
                try (InputStream fis = new BufferedInputStream(new FileInputStream(stagingFile))) {
                    byte[] buffer = new byte[ProtocolConstants.CHUNK_SIZE];
                    int bytesRead;
                    int chunkIndex = 0;
                    long totalBytesSent = 0;
                    long fileLength = stagingFile.length();
                    long lastSpeedTime = System.currentTimeMillis();
                    long bytesSinceLastSpeed = 0;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        if (transferAborted.get()) {
                            updateStatus(TransferState.FAILED, "ĐÃ HỦY TRUYỀN FILE", 0.0);
                            return;
                        }

                        byte[] chunkPayload = (bytesRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                        ProtocolMessage chunkMsg = ProtocolMessage.createFileData(currentSendingInfo.getFileId(), chunkIndex, chunkPayload);
                        sender.sendSync(chunkMsg);

                        chunkIndex++;
                        totalBytesSent += bytesRead;
                        bytesSinceLastSpeed += bytesRead;

                        double currentProgress = fileLength > 0 ? (double) totalBytesSent / fileLength : 1.0;

                        // Cập nhật tốc độ mỗi 300ms
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
                        final String stat = String.format("Đã gửi: %d/%d chunk (%.1f%%)",
                                chunkIndex, currentSendingInfo.getTotalChunks(), prog * 100.0);
                        ClientUtils.runOnFxThread(() -> {
                            progress.set(prog);
                            statusText.set(stat);
                        });
                    }

                    // Gửi thông báo hoàn tất truyền toàn bộ chunk
                    ProtocolMessage completeMsg = ProtocolMessage.createText(MessageType.FILE_COMPLETE, currentSendingInfo.toJson());
                    sender.sendSync(completeMsg);

                    updateStatus(TransferState.VERIFYING, "ĐÃ GỬI XONG. CHỜ BÊN NHẬN KIỂM TRA SHA-256...", 1.0);
                }

            } catch (IOException e) {
                updateStatus(TransferState.FAILED, "LỖI: Truyền chunk thất bại: " + e.getMessage(), 0.0);
            }
        });
    }

    // Người nhận từ chối nhận file
    public void onFileRejectedByPeer(FileInfo info) {
        updateStatus(TransferState.REJECTED, "NGƯỜI NHẬN ĐÃ TỪ CHỐI NHẬN FILE", 0.0);
    }

    // Xử lý khi có người gửi file đến mình: Hiển thị popup hỏi ý kiến
    public void onIncomingFileRequest(FileInfo info) {
        currentReceivingInfo = info;
        ClientUtils.runOnFxThread(() -> {
            activeFileName.set(info.getFileName() + " (" + FileUtils.formatFileSize(info.getFileSize()) + ")");
            statusText.set("CÓ FILE GỬI TỪ " + info.getSenderName());
            checksumResult.set("");

            if (incomingRequestListener != null) {
                incomingRequestListener.onIncomingFileRequest(info, (accepted, destFile) -> {
                    if (accepted && destFile != null) {
                        try {
                            receivingFile = destFile;
                            receivingOutputStream = new FileOutputStream(receivingFile);
                            totalBytesReceived = 0;
                            updateStatus(TransferState.TRANSFERRING, "CHUẨN BỊ NHẬN DỮ LIỆU...", 0.0);

                            // Gửi thông báo đồng ý nhận file
                            ProtocolMessage acceptMsg = ProtocolMessage.createText(MessageType.FILE_ACCEPT, info.toJson());
                            sender.sendAsync(acceptMsg);

                        } catch (IOException e) {
                            updateStatus(TransferState.FAILED, "LỖI: Không thể tạo file lưu: " + e.getMessage(), 0.0);
                        }
                    } else {
                        updateStatus(TransferState.IDLE, "ĐÃ TỪ CHỐI NHẬN FILE", 0.0);
                        ProtocolMessage rejectMsg = ProtocolMessage.createText(MessageType.FILE_REJECT, info.toJson());
                        sender.sendAsync(rejectMsg);
                    }
                });
            }
        });
    }

    // Nhận thông số metadata trước khi các chunk dữ liệu bắt đầu đến
    public void onIncomingFileMetadata(FileInfo info) {
        currentReceivingInfo = info;
        updateStatus(TransferState.TRANSFERRING, "ĐANG NHẬN DỮ LIỆU...", 0.0);
    }

    // Nhận một chunk nhị phân và ghi trực tiếp vào file trên đĩa
    public void onIncomingFileData(ProtocolMessage.FileChunk chunk) {
        if (receivingOutputStream == null || receivingFile == null) return;

        try {
            receivingOutputStream.write(chunk.data());
            totalBytesReceived += chunk.data().length;

            long totalExpected = (currentReceivingInfo != null) ? currentReceivingInfo.getFileSize() : 1;
            double currentProgress = totalExpected > 0 ? (double) totalBytesReceived / totalExpected : 0.0;

            final double prog = Math.min(1.0, currentProgress);
            final String stat = String.format("Đã nhận: %s / %s (%.1f%%)",
                    FileUtils.formatFileSize(totalBytesReceived),
                    FileUtils.formatFileSize(totalExpected),
                    prog * 100.0);

            ClientUtils.runOnFxThread(() -> {
                progress.set(prog);
                statusText.set(stat);
            });

        } catch (IOException e) {
            updateStatus(TransferState.FAILED, "LỖI: Không thể ghi file: " + e.getMessage(), 0.0);
            closeReceivingStream();
        }
    }

    // Nhận thông báo hoàn tất: Đóng file, tính lại SHA-256 để đối soát tính toàn vẹn
    public void onIncomingFileComplete(FileInfo info) {
        closeReceivingStream();
        updateStatus(TransferState.VERIFYING, "ĐANG KIỂM TRA MÃ SHA-256...", 1.0);

        transferPool.submit(() -> {
            try {
                String localHash = ChecksumUtils.calculateSHA256(receivingFile);
                String expectedHash = (currentReceivingInfo != null) ? currentReceivingInfo.getChecksum() : info.getChecksum();

                boolean match = ChecksumUtils.verifyChecksum(expectedHash, localHash);

                ClientUtils.runOnFxThread(() -> {
                    if (match) {
                        state.set(TransferState.COMPLETED);
                        statusText.set("TOÀN VẸN DỮ LIỆU // SHA-256 KHỚP 100%");
                        checksumResult.set("THÀNH CÔNG: " + localHash.substring(0, 16) + "...");
                    } else {
                        state.set(TransferState.FAILED);
                        statusText.set("DỮ LIỆU BỊ LỖI // SHA-256 KHÔNG KHỚP");
                        checksumResult.set("LỖI: GỐC=" + expectedHash.substring(0, 8) + "... NHẬN=" + localHash.substring(0, 8) + "...");
                    }
                });

                // Phản hồi kết quả đối soát về cho người gửi
                FileInfo statusReport = new FileInfo();
                statusReport.setFileId(info.getFileId());
                statusReport.setSenderId(info.getSenderId());
                statusReport.setChecksum(match ? "VERIFIED" : "CORRUPT");
                ProtocolMessage statusMsg = ProtocolMessage.createText(MessageType.FILE_STATUS, statusReport.toJson());
                sender.sendSync(statusMsg);

            } catch (IOException e) {
                updateStatus(TransferState.FAILED, "LỖI: Không thể kiểm tra SHA-256: " + e.getMessage(), 0.0);
            }
        });
    }

    // Người gửi nhận thông báo kết quả đối soát SHA-256 từ bên nhận
    public void onFileStatusReceived(FileInfo info) {
        ClientUtils.runOnFxThread(() -> {
            if ("VERIFIED".equalsIgnoreCase(info.getChecksum())) {
                state.set(TransferState.COMPLETED);
                statusText.set("BÊN NHẬN ĐÃ XÁC NHẬN SHA-256 KHỚP 100%");
                checksumResult.set("KẾT QUẢ: TOÀN VẸN [THÀNH CÔNG]");
            } else {
                state.set(TransferState.FAILED);
                statusText.set("BÊN NHẬN BÁO CÁO FILE BỊ LỖI");
                checksumResult.set("KẾT QUẢ: LỖI TOÀN VẸN [THẤT BẠI]");
            }
        });
    }

    // Hủy bỏ quá trình truyền file
    public void abortTransfer() {
        transferAborted.set(true);
        closeReceivingStream();
        updateStatus(TransferState.IDLE, "ĐÃ HỦY TRUYỀN", 0.0);
    }

    private void updateStatus(TransferState newState, String text, double prog) {
        ClientUtils.runOnFxThread(() -> {
            state.set(newState);
            statusText.set(text);
            progress.set(prog);
        });
    }

    private void closeReceivingStream() {
        try {
            if (receivingOutputStream != null) {
                receivingOutputStream.flush();
                receivingOutputStream.close();
            }
        } catch (IOException ignored) {}
        receivingOutputStream = null;
    }
}
