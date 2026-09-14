package com.peerchat.client.controller;

import com.peerchat.client.model.TransferState;
import com.peerchat.client.service.FileTransferService;
import com.peerchat.client.util.ClientUtils;
import com.peerchat.shared.model.ClientInfo;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.protocol.ProtocolConstants;
import com.peerchat.shared.util.FileUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Optional;

// Điều khiển giao diện truyền file (gửi, nhận, tiến trình, kiểm tra SHA-256)
public class FileTransferController {
    @FXML private Label targetRecipientLabel;
    @FXML private Label stagedFileLabel;
    @FXML private Label speedLabel;
    @FXML private Label statusLabel;
    @FXML private Label checksumLabel;
    @FXML private ProgressBar transferProgressBar;
    @FXML private Button selectFileButton;
    @FXML private Button sendButton;
    @FXML private Button abortButton;

    private FileTransferService fileTransferService;
    private File stagedFile;
    private String selectedTargetClientId = ProtocolConstants.TARGET_ALL;

    // Khởi tạo và liên kết các thuộc tính tiến trình truyền file với giao diện
    public void initService(FileTransferService fileTransferService) {
        this.fileTransferService = fileTransferService;

        // Ràng buộc thuộc tính tiến trình và trạng thái
        transferProgressBar.progressProperty().bind(fileTransferService.progressProperty());
        speedLabel.textProperty().bind(fileTransferService.telemetrySpeedProperty());
        statusLabel.textProperty().bind(fileTransferService.statusTextProperty());
        checksumLabel.textProperty().bind(fileTransferService.checksumResultProperty());

        fileTransferService.stateProperty().addListener((obs, oldState, newState) -> {
            boolean active = (newState == TransferState.TRANSFERRING || newState == TransferState.REQUESTING || newState == TransferState.WAITING_ACCEPT);
            abortButton.setDisable(!active);
            selectFileButton.setDisable(active);
            sendButton.setDisable(active || stagedFile == null);
        });
    }

    // Thiết lập người nhận file mục tiêu
    public void setSelectedTarget(ClientInfo peer) {
        if (peer == null) {
            this.selectedTargetClientId = ProtocolConstants.TARGET_ALL;
            targetRecipientLabel.setText("TẤT CẢ NGƯỜI DÙNG (BROADCAST)");
        } else {
            this.selectedTargetClientId = peer.getClientId();
            targetRecipientLabel.setText(peer.getDisplayName() + " [" + peer.getCoordinates() + "]");
        }
    }

    // Mở hộp thoại chọn file từ máy tính
    @FXML
    private void handleSelectFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("CHỌN FILE ĐỂ TRUYỀN");
        File file = chooser.showOpenDialog(selectFileButton.getScene().getWindow());

        if (file != null && file.exists()) {
            stagedFile = file;
            stagedFileLabel.setText(file.getName() + " (" + FileUtils.formatFileSize(file.length()) + ")");
            sendButton.setDisable(false);
        }
    }

    // Bắt đầu gửi file đã chọn
    @FXML
    private void handleSendFile() {
        if (stagedFile == null || fileTransferService == null) return;
        fileTransferService.stageAndSendFile(stagedFile, selectedTargetClientId);
    }

    // Hủy phiên truyền file hiện tại
    @FXML
    private void handleAbort() {
        if (fileTransferService != null) {
            fileTransferService.abortTransfer();
        }
    }
}
