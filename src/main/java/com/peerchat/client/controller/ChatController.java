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
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Điều khiển màn hình chat chính và quản lý tương tác người dùng
public class ChatController {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Kênh chung mặc định cố định ở đầu danh sách
    private static final ClientInfo CHANNEL_BROADCAST = new ClientInfo(
            ProtocolConstants.TARGET_ALL,
            "● KÊNH CHUNG (TẤT CẢ PHÒNG)",
            "ALL",
            0
    );

    @FXML private Label nodeCallsignLabel;
    @FXML private Label serverCoordinatesLabel;
    @FXML private Label carrierStatusLabel;
    @FXML private Label peerCountLabel;
    @FXML private Label channelTargetLabel;
    @FXML private Label systemClockLabel;

    @FXML private Button toggleRepoButton;
    @FXML private SplitPane mainSplitPane;
    @FXML private ListView<ClientInfo> peersListView;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox messagesContainer;
    @FXML private TextArea messageInputField;
    @FXML private Button sendButton;

    // Nhúng controller con của phần kho tài liệu phòng
    @FXML private VBox fileTransfer;
    @FXML private FileTransferController fileTransferController;

    private ClientSocket clientSocket;
    private MessageSender messageSender;
    private MessageReceiver messageReceiver;
    private ChatService chatService;
    private FileTransferService fileTransferService;

    private final ObservableList<ClientInfo> displayPeers = FXCollections.observableArrayList();
    private ClientInfo activeSelectedPeer = null;
    private boolean isRepoOpen = true;
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
            fileTransferController.initData(fileTransferService, chatService, this::handleToggleRepo);
        }

        setupPeerDirectory();
        setupChatStream();
        setupDragAndDrop();
        setupKeyHandlers();
        setupNetworkListener();
        startSystemClock();
    }

    // Thiết lập danh sách người dùng với hàng mục Kênh Chung cố định ở đầu
    private void setupPeerDirectory() {
        peersListView.setItems(displayPeers);
        rebuildDisplayPeers();

        // Lắng nghe thay đổi danh sách online từ chatService
        chatService.getOnlinePeers().addListener((ListChangeListener<ClientInfo>) c -> {
            rebuildDisplayPeers();
            int count = chatService.getOnlinePeers().size();
            peerCountLabel.setText(count + " NGƯỜI DÙNG ONLINE");
        });

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

                    boolean isBroadcast = ProtocolConstants.TARGET_ALL.equals(item.getClientId());

                    Label statusDot = new Label(isBroadcast ? "●" : "■");
                    if (isBroadcast) {
                        statusDot.setStyle(isSelected() ? "-fx-text-fill: #b8522e; -fx-font-size: 11px;" : "-fx-text-fill: #1f4f6e; -fx-font-size: 11px;");
                    } else {
                        statusDot.setStyle(isSelected() ? "-fx-text-fill: #b8522e; -fx-font-size: 10px;" : "-fx-text-fill: #3e7238; -fx-font-size: 10px;");
                    }

                    Label nameLabel = new Label(item.getDisplayName());
                    nameLabel.setStyle(isSelected() ? "-fx-text-fill: #ffffff; -fx-font-weight: bold;" : "-fx-text-fill: #1c1b17; -fx-font-weight: bold;");

                    cellBox.getChildren().addAll(statusDot, nameLabel);

                    if (!isBroadcast) {
                        Label coordLabel = new Label("[" + item.getCoordinates() + "]");
                        coordLabel.setStyle(isSelected() ? "-fx-text-fill: #c5c0af; -fx-font-size: 10px;" : "-fx-text-fill: #615c4f; -fx-font-size: 10px;");
                        cellBox.getChildren().add(coordLabel);
                    }

                    setGraphic(cellBox);
                }
            }
        });

        // Xử lý khi chọn người dùng để chat riêng hoặc quay về kênh chung
        peersListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || ProtocolConstants.TARGET_ALL.equals(newVal.getClientId())) {
                activeSelectedPeer = null;
                channelTargetLabel.setText("KÊNH CHUNG (TẤT CẢ PHÒNG)");
            } else {
                activeSelectedPeer = newVal;
                channelTargetLabel.setText("CHAT RIÊNG VỚI // " + newVal.getDisplayName() + " [" + newVal.getCoordinates() + "]");
            }
        });

        // Mặc định chọn dòng đầu tiên (Kênh chung)
        peersListView.getSelectionModel().select(0);
    }

    // Tái cấu trúc danh sách hiển thị với Kênh Chung luôn ở dòng đầu
    private void rebuildDisplayPeers() {
        ClientInfo currentSelected = peersListView.getSelectionModel().getSelectedItem();
        displayPeers.clear();
        displayPeers.add(CHANNEL_BROADCAST);
        displayPeers.addAll(chatService.getOnlinePeers());

        if (currentSelected != null && !ProtocolConstants.TARGET_ALL.equals(currentSelected.getClientId())) {
            peersListView.getSelectionModel().select(currentSelected);
        } else {
            peersListView.getSelectionModel().select(0);
        }
    }

    // Cấu hình tính năng kéo thả file trực tiếp vào khung chat hoặc ô nhập tin nhắn
    private void setupDragAndDrop() {
        setupDragAndDropForNode(chatScrollPane);
        setupDragAndDropForNode(messageInputField);
    }

    // Đăng ký sự kiện Drag and Drop cho từng vùng giao diện
    private void setupDragAndDropForNode(Node node) {
        node.setOnDragOver(event -> {
            if (event.getGestureSource() != node && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                if (!node.getStyleClass().contains("drag-over-active")) {
                    node.getStyleClass().add("drag-over-active");
                }
            }
            event.consume();
        });

        node.setOnDragExited(event -> {
            node.getStyleClass().remove("drag-over-active");
            event.consume();
        });

        node.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    stageAndSendDroppedFile(file);
                }
            }
            node.getStyleClass().remove("drag-over-active");
            event.setDropCompleted(success);
            event.consume();
        });
    }

    // Đẩy tập tin kéo thả lên server để chia sẻ
    private void stageAndSendDroppedFile(File file) {
        if (file != null && file.exists() && file.isFile()) {
            String targetId = (activeSelectedPeer != null) ? activeSelectedPeer.getClientId() : ProtocolConstants.TARGET_ALL;
            fileTransferService.stageAndSendFile(file, targetId);
        }
    }

    // Cài đặt phím tắt bàn phím (Enter: Gửi tin nhắn, Shift+Enter: Xuống dòng)
    private void setupKeyHandlers() {
        messageInputField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    int caret = messageInputField.getCaretPosition();
                    messageInputField.insertText(caret, "\n");
                    event.consume();
                } else {
                    event.consume();
                    handleSendMessage();
                }
            }
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

    // Hiển thị một thẻ tin nhắn lên khung chat (phân biệt mình gửi vs người khác gửi)
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

    // Ẩn/hiện panel Kho Tài Liệu Phòng để mở rộng khung chat
    @FXML
    private void handleToggleRepo() {
        if (isRepoOpen) {
            mainSplitPane.getItems().remove(fileTransfer);
            toggleRepoButton.setText("📁 KHO TÀI LIỆU");
            isRepoOpen = false;
        } else {
            if (!mainSplitPane.getItems().contains(fileTransfer)) {
                mainSplitPane.getItems().add(fileTransfer);
                mainSplitPane.setDividerPositions(0.22, 0.72);
            }
            toggleRepoButton.setText("📁 ĐÓNG KHO");
            isRepoOpen = true;
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

