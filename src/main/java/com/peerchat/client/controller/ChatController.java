package com.peerchat.client.controller;

import com.peerchat.client.network.ClientSocket;
import com.peerchat.client.network.MessageReceiver;
import com.peerchat.client.network.MessageSender;
import com.peerchat.client.service.ChatService;
import com.peerchat.client.service.FileTransferService;
import com.peerchat.client.util.ClientUtils;
import com.peerchat.shared.model.ClientInfo;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.model.Message;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolConstants;
import com.peerchat.shared.protocol.ProtocolMessage;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Điều khiển màn hình chat và quản lý người dùng
public class ChatController {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML private Label nodeCallsignLabel;
    @FXML private Label serverCoordinatesLabel;
    @FXML private Label carrierStatusLabel;
    @FXML private Label peerCountLabel;
    @FXML private Label channelTargetLabel;
    @FXML private Label systemClockLabel;

    @FXML private Button broadcastButton;
    @FXML private ListView<ClientInfo> peersListView;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox messagesContainer;
    @FXML private TextField messageInputField;
    @FXML private Button attachFileButton;
    @FXML private Button sendButton;

    // Nhúng controller con của phần truyền file
    @FXML private FileTransferController fileTransferController;

    private ClientSocket clientSocket;
    private MessageSender messageSender;
    private MessageReceiver messageReceiver;
    private ChatService chatService;
    private FileTransferService fileTransferService;

    private ClientInfo activeSelectedPeer = null;
    private Timeline clockTimeline;

    // Khởi tạo dữ liệu và kết nối dịch vụ cho màn hình chat
    public void initData(ClientSocket clientSocket,
                         MessageSender sender,
                         MessageReceiver receiver,
                         ChatService chatService,
                         FileTransferService fileTransferService,
                         String serverCoordinates) {
        this.clientSocket = clientSocket;
        this.messageSender = sender;
        this.messageReceiver = receiver;
        this.chatService = chatService;
        this.fileTransferService = fileTransferService;

        nodeCallsignLabel.setText(chatService.getDisplayName().toUpperCase());
        serverCoordinatesLabel.setText(serverCoordinates);
        carrierStatusLabel.setText("ĐÃ KẾT NỐI");

        if (fileTransferController != null) {
            fileTransferController.initService(fileTransferService);
            fileTransferController.setSelectedTarget(null);
        }

        setupPeerDirectory();
        setupChatStream();
        setupNetworkListener();
        startSystemClock();
    }

    // Thiết lập danh sách người dùng đang online
    private void setupPeerDirectory() {
        peersListView.setItems(chatService.getOnlinePeers());

        // Định dạng hiển thị từng dòng người dùng
        peersListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ClientInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox cellBox = new HBox(8);
                    cellBox.setAlignment(Pos.CENTER_LEFT);

                    Label statusDot = new Label("■");
                    statusDot.setStyle(isSelected() ? "-fx-text-fill: #b8522e; -fx-font-size: 10px;" : "-fx-text-fill: #3e7238; -fx-font-size: 10px;");

                    Label nameLabel = new Label(item.getDisplayName());
                    nameLabel.setStyle(isSelected() ? "-fx-text-fill: #ffffff; -fx-font-weight: bold;" : "-fx-text-fill: #1c1b17; -fx-font-weight: bold;");

                    Label coordLabel = new Label("[" + item.getCoordinates() + "]");
                    coordLabel.setStyle(isSelected() ? "-fx-text-fill: #c5c0af; -fx-font-size: 10px;" : "-fx-text-fill: #615c4f; -fx-font-size: 10px;");

                    cellBox.getChildren().addAll(statusDot, nameLabel, coordLabel);
                    setGraphic(cellBox);
                }
            }
        });

        // Xử lý khi chọn người dùng để chat riêng
        peersListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                activeSelectedPeer = newVal;
                channelTargetLabel.setText("CHAT RIÊNG VỚI // " + newVal.getDisplayName() + " [" + newVal.getCoordinates() + "]");
                if (fileTransferController != null) {
                    fileTransferController.setSelectedTarget(newVal);
                }
            }
        });

        // Cập nhật số lượng người online khi danh sách thay đổi
        chatService.getOnlinePeers().addListener((ListChangeListener<ClientInfo>) c -> {
            int count = chatService.getOnlinePeers().size();
            peerCountLabel.setText(count + " NGƯỜI DÙNG ONLINE");
        });
    }

    // Lắng nghe danh sách tin nhắn để cập nhật lên giao diện
    private void setupChatStream() {
        chatService.getMessageHistory().addListener((ListChangeListener<Message>) change -> {
            while (change.next()) {
                if (change.wasRemoved() && chatService.getMessageHistory().isEmpty()) {
                    messagesContainer.getChildren().clear();
                }
                if (change.wasAdded()) {
                    for (Message msg : change.getAddedSubList()) {
                        renderMessageCard(msg);
                    }
                }
            }
        });
    }

    // Hiển thị một bong bóng tin nhắn lên khung chat (phân biệt mình vs người khác)
    private void renderMessageCard(Message msg) {
        boolean isOutgoing = chatService.getClientId().equals(msg.getSenderId());

        // HBox bọc toàn bộ dòng tin nhắn để căn lề trái hoặc phải
        HBox row = new HBox();
        row.getStyleClass().add(isOutgoing ? "message-row-outgoing" : "message-row-incoming");
        row.setMaxWidth(Double.MAX_VALUE);

        // Khung card tin nhắn
        VBox card = new VBox(6);
        card.getStyleClass().add(isOutgoing ? "message-card-outgoing" : "message-card-incoming");
        card.setMaxWidth(520);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        if (!isOutgoing) {
            // Avatar huy hiệu ký tự đầu cho người khác
            String initial = (msg.getSenderName() != null && !msg.getSenderName().isEmpty())
                    ? msg.getSenderName().substring(0, 1).toUpperCase()
                    : "?";
            Label avatar = new Label(initial);
            avatar.getStyleClass().add("avatar-badge");
            header.getChildren().add(avatar);
        }

        Label senderLabel = new Label(isOutgoing ? "BẠN [" + chatService.getDisplayName() + "]" : msg.getSenderName());
        senderLabel.getStyleClass().add(isOutgoing ? "message-header-outgoing" : "message-header-incoming");

        Label targetTag = new Label(msg.isDirect() ? "[RIÊNG]" : "[CHUNG]");
        targetTag.setStyle("-fx-text-fill: #615c4f; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label timeLabel = new Label(msg.getFormattedTime());
        timeLabel.getStyleClass().add("message-timestamp");

        header.getChildren().addAll(senderLabel, targetTag, timeLabel);
        card.getChildren().add(header);

        // Hiển thị nội dung tin nhắn (tin nhắn văn bản hoặc thông báo tệp đã chia sẻ)
        String text = msg.isFileMessage() && msg.getFileInfo() != null
                ? "🗎 [TẬP TIN ĐÃ CHIA SẺ] " + msg.getFileInfo().getFileName() + " (" + com.peerchat.shared.util.FileUtils.formatFileSize(msg.getFileInfo().getFileSize()) + ")"
                : msg.getContent();
        Label body = new Label(text);
        body.getStyleClass().add("message-body");
        body.setWrapText(true);
        card.getChildren().add(body);

        row.getChildren().add(card);
        messagesContainer.getChildren().add(row);

        // Cuộn xuống tin nhắn mới nhất
        Platform.runLater(() -> {
            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);
        });
    }

    // Mở hộp thoại đính kèm tập tin và tải lên máy chủ
    @FXML
    private void handleAttachFile() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("CHỌN TẬP TIN GỬI LÊN MÁY CHỦ");
        File file = chooser.showOpenDialog(chatScrollPane.getScene().getWindow());

        if (file != null && file.exists()) {
            String targetId = (activeSelectedPeer != null) ? activeSelectedPeer.getClientId() : ProtocolConstants.TARGET_ALL;
            fileTransferService.stageAndSendFile(file, targetId);
        }
    }

    // Đăng ký nhận các gói tin từ server và phân luồng xử lý
    private void setupNetworkListener() {
        messageReceiver.addListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessageReceived(ProtocolMessage message) {
                switch (message.getType()) {
                    case CLIENT_LIST_UPDATE -> {
                        List<ClientInfo> list = ClientInfo.listFromJson(message.getPayloadAsText());
                        chatService.updateOnlinePeers(list);
                    }
                    case CHAT_BROADCAST -> {
                        Message chatMsg = Message.fromJson(message.getPayloadAsText());
                        chatService.onChatMessageReceived(chatMsg);
                    }
                    case CHAT_HISTORY -> {
                        List<Message> history = Message.listFromJson(message.getPayloadAsText());
                        chatService.setHistory(history);
                    }
                    case FILE_DATA -> {
                        fileTransferService.onIncomingFileData(message.parseFileData());
                    }
                    case FILE_DOWNLOAD_COMPLETE, FILE_COMPLETE -> {
                        FileInfo info = FileInfo.fromJson(message.getPayloadAsText());
                        fileTransferService.onIncomingFileComplete(info);
                    }
                    case FILE_STATUS -> {
                        FileInfo info = FileInfo.fromJson(message.getPayloadAsText());
                        fileTransferService.onFileStatusReceived(info);
                    }
                    default -> {}
                }
            }

            @Override
            public void onConnectionLost(String reason) {
                ClientUtils.runOnFxThread(() -> {
                    carrierStatusLabel.setText("MẤT KẾT NỐI");
                    carrierStatusLabel.setStyle("-fx-text-fill: #b8522e; -fx-font-weight: bold;");
                    ClientUtils.showAlert(Alert.AlertType.ERROR, "PEERCHAT // MẤT KẾT NỐI",
                            "KẾT NỐI BỊ NGẮT", reason);
                });
            }
        });

        // Bắt đầu luồng đọc gói tin nền
        messageReceiver.start();
    }

    // Chọn lại chế độ chat chung cho toàn phòng
    @FXML
    private void handleSelectBroadcast() {
        peersListView.getSelectionModel().clearSelection();
        activeSelectedPeer = null;
        channelTargetLabel.setText("KÊNH CHUNG (TẤT CẢ)");
        if (fileTransferController != null) {
            fileTransferController.setSelectedTarget(null);
        }
    }

    // Gửi tin nhắn văn bản đi
    @FXML
    private void handleSendMessage() {
        String content = messageInputField.getText();
        if (content == null || content.trim().isEmpty()) return;

        String targetId = (activeSelectedPeer != null) ? activeSelectedPeer.getClientId() : ProtocolConstants.TARGET_ALL;
        chatService.sendMessage(content, targetId);
        messageInputField.clear();
        messageInputField.requestFocus();
    }

    // Ngắt kết nối và đóng cửa sổ
    @FXML
    private void handleDisconnect() {
        if (messageSender != null) {
            try {
                messageSender.sendSync(new ProtocolMessage(MessageType.DISCONNECT, new byte[0]));
            } catch (Exception ignored) {}
        }
        if (clientSocket != null) {
            clientSocket.disconnect();
        }
        if (clockTimeline != null) {
            clockTimeline.stop();
        }

        Stage stage = (Stage) nodeCallsignLabel.getScene().getWindow();
        stage.close();
    }

    // Đồng hồ hệ thống thời gian thực
    private void startSystemClock() {
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            systemClockLabel.setText("GIỜ HT: " + LocalDateTime.now().format(CLOCK_FORMAT));
        }));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }
}
