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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Điều khiển màn hình chat chính và quản lý tương tác người dùng
public class ChatController {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Kênh chung mặc định cố định ở đầu danh sách
    private static final ClientInfo CHANNEL_BROADCAST = new ClientInfo(
            ProtocolConstants.TARGET_ALL,
            "● KÊNH CHUNG",
            "ALL",
            0
    );

    // Bộ nhớ đệm ảnh xem trước và thư mục tạm lưu trữ ảnh preview
    private static final Map<String, Image> imageCache = new ConcurrentHashMap<>();
    private static final File CACHE_DIR = new File(System.getProperty("java.io.tmpdir"), "peerchat_cache");
    static {
        CACHE_DIR.mkdirs();
    }

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
    @FXML private HBox disconnectBanner;
    @FXML private Button attachFileButton;
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
            peerCountLabel.setText(count + " NGƯỜI TRỰC TUYẾN");
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
                channelTargetLabel.setText("KÊNH CHUNG");
            } else {
                activeSelectedPeer = newVal;
                channelTargetLabel.setText("TRÒ CHUYỆN RIÊNG VỚI: " + newVal.getDisplayName() + " [" + newVal.getCoordinates() + "]");
            }
            reloadMessagesForActiveChannel();
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

    // Tải lại các tin nhắn tương ứng với kênh đang mở (Kênh chung hoặc Chat riêng)
    private void reloadMessagesForActiveChannel() {
        messagesContainer.getChildren().clear();
        for (Message msg : chatService.getMessageHistory()) {
            if (isMessageBelongToActiveChannel(msg)) {
                renderMessageCard(msg);
            }
        }
    }

    // Kiểm tra tin nhắn có thuộc về cuộc trò chuyện hiện tại hay không
    private boolean isMessageBelongToActiveChannel(Message msg) {
        if (msg == null) return false;
        String myId = chatService.getClientId();
        if (activeSelectedPeer == null) {
            // Kênh chung: chỉ nhận các tin nhắn gửi tới ALL
            return !msg.isDirect() || ProtocolConstants.TARGET_ALL.equalsIgnoreCase(msg.getTargetId());
        } else {
            // Chat riêng: chỉ nhận tin trao đổi giữa tôi và activeSelectedPeer
            String peerId = activeSelectedPeer.getClientId();
            boolean outgoing = myId.equals(msg.getSenderId()) && peerId.equals(msg.getTargetId());
            boolean incoming = peerId.equals(msg.getSenderId()) && myId.equals(msg.getTargetId());
            return msg.isDirect() && (outgoing || incoming);
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

    // Mở hộp thoại chọn file đính kèm gửi vào cuộc trò chuyện hiện tại
    @FXML
    private void handleAttachFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("CHỌN TẬP TIN GỬI");
        File file = chooser.showOpenDialog(chatScrollPane.getScene().getWindow());
        if (file != null && file.exists()) {
            stageAndSendDroppedFile(file);
        }
    }

    // Đẩy tập tin kéo thả lên server để chia sẻ vào cuộc trò chuyện đang chọn
    private void stageAndSendDroppedFile(File file) {
        if (file != null && file.exists() && file.isFile()) {
            if (FileTransferController.isImageFile(file.getName())) {
                try {
                    imageCache.put(file.getName(), new Image(file.toURI().toString()));
                } catch (Exception ignored) {}
            }
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

    // Lắng nghe danh sách tin nhắn để cập nhật lên giao diện theo kênh đang mở
    private void setupChatStream() {
        chatService.getMessageHistory().addListener((ListChangeListener<Message>) change -> {
            while (change.next()) {
                if (change.wasRemoved() && chatService.getMessageHistory().isEmpty()) {
                    messagesContainer.getChildren().clear();
                }
                if (change.wasAdded()) {
                    for (Message msg : change.getAddedSubList()) {
                        if (isMessageBelongToActiveChannel(msg)) {
                            renderMessageCard(msg);
                        }
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

        if (msg.isFileMessage() && msg.getFileInfo() != null) {
            FileInfo fileInfo = msg.getFileInfo();
            if (FileTransferController.isImageFile(fileInfo.getFileName())) {
                renderImageCard(card, fileInfo);
            } else {
                renderFileCard(card, fileInfo);
            }
        } else {
            Label body = new Label(msg.getContent());
            body.getStyleClass().add("message-body");
            body.setWrapText(true);
            card.getChildren().add(body);
        }

        row.getChildren().add(card);
        messagesContainer.getChildren().add(row);

        // Cuộn xuống tin nhắn mới nhất
        Platform.runLater(() -> {
            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);
        });
    }

    // Hiển thị hình ảnh xem trước trực tiếp kèm nút tải về
    private void renderImageCard(VBox card, FileInfo fileInfo) {
        VBox imageBox = new VBox(6);
        imageBox.getStyleClass().add("chat-image-box");

        ImageView imageView = new ImageView();
        imageView.setFitWidth(320);
        imageView.setFitHeight(220);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.getStyleClass().add("chat-image-preview");

        Label loadingLabel = new Label("⏳ ĐANG TẢI HÌNH ẢNH...");
        loadingLabel.getStyleClass().add("file-card-meta");

        Button downloadBtn = new Button("⬇ TẢI VỀ");
        downloadBtn.getStyleClass().add("file-card-btn");

        File cacheFile = new File(CACHE_DIR, fileInfo.getFileId() + "_" + fileInfo.getFileName());

        Image cachedImg = imageCache.get(fileInfo.getFileId());
        if (cachedImg == null) {
            cachedImg = imageCache.get(fileInfo.getFileName());
        }

        if (cachedImg != null) {
            imageView.setImage(cachedImg);
            imageBox.getChildren().add(imageView);
        } else if (cacheFile.exists() && cacheFile.length() > 0) {
            Image diskImg = new Image(cacheFile.toURI().toString());
            imageCache.put(fileInfo.getFileId(), diskImg);
            imageView.setImage(diskImg);
            imageBox.getChildren().add(imageView);
        } else {
            imageBox.getChildren().add(loadingLabel);
            fileTransferService.requestDownload(fileInfo, cacheFile, new FileTransferService.DownloadCallback() {
                @Override
                public void onProgress(double progress, long currentBytes, long totalBytes) {}

                @Override
                public void onComplete(boolean success, String hash, String error) {
                    ClientUtils.runOnFxThread(() -> {
                        if (success && cacheFile.exists()) {
                            Image diskImg = new Image(cacheFile.toURI().toString());
                            imageCache.put(fileInfo.getFileId(), diskImg);
                            imageView.setImage(diskImg);
                            imageBox.getChildren().remove(loadingLabel);
                            if (!imageBox.getChildren().contains(imageView)) {
                                imageBox.getChildren().add(0, imageView);
                            }
                        } else if ("FILE_NOT_FOUND".equals(error)) {
                            loadingLabel.setText("[ DỮ LIỆU ĐÃ MẤT ] HÌNH ẢNH KHÔNG CÒN TỒN TẠI TRÊN MÁY CHỦ");
                            downloadBtn.setText("FILE ĐÃ MẤT");
                            downloadBtn.setDisable(true);
                        } else {
                            loadingLabel.setText("❌ Không thể tải ảnh xem trước");
                        }
                    });
                }
            });
        }

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label("🖼 " + fileInfo.getFileName() + " (" + com.peerchat.shared.util.FileUtils.formatFileSize(fileInfo.getFileSize()) + ")");
        nameLabel.getStyleClass().add("file-card-title");
        nameLabel.setMaxWidth(220);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        downloadBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("LƯU HÌNH ẢNH");
            chooser.setInitialFileName(fileInfo.getFileName());
            File dest = chooser.showSaveDialog(chatScrollPane.getScene().getWindow());

            if (dest != null) {
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    try {
                        java.nio.file.Files.copy(cacheFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        downloadBtn.setText("📂 MỞ THƯ MỤC");
                        downloadBtn.setOnAction(ev -> openDirectory(dest.getParentFile()));
                    } catch (Exception ex) {
                        downloadBtn.setText("LỖI LƯU");
                    }
                } else {
                    downloadBtn.setDisable(true);
                    downloadBtn.setText("ĐANG TẢI...");
                    fileTransferService.requestDownload(fileInfo, dest, new FileTransferService.DownloadCallback() {
                        @Override
                        public void onProgress(double progress, long currentBytes, long totalBytes) {}

                        @Override
                        public void onComplete(boolean success, String hash, String error) {
                            ClientUtils.runOnFxThread(() -> {
                                if (success) {
                                    downloadBtn.setText("📂 MỞ THƯ MỤC");
                                    downloadBtn.setDisable(false);
                                    downloadBtn.setOnAction(ev -> openDirectory(dest.getParentFile()));
                                } else if ("FILE_NOT_FOUND".equals(error)) {
                                    downloadBtn.setText("FILE ĐÃ MẤT");
                                    downloadBtn.setDisable(true);
                                } else {
                                    downloadBtn.setText("TẢI LẠI");
                                    downloadBtn.setDisable(false);
                                }
                            });
                        }
                    });
                }
            }
        });

        metaRow.getChildren().addAll(nameLabel, spacer, downloadBtn);
        card.getChildren().addAll(imageBox, metaRow);
    }

    // Hiển thị thẻ tập tin thông thường (tối giản, không icon nhãn rườm rà)
    private void renderFileCard(VBox card, FileInfo fileInfo) {
        VBox fileCard = new VBox(6);
        fileCard.getStyleClass().add("file-card");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("🗎 " + fileInfo.getFileName());
        titleLabel.getStyleClass().add("file-card-title");
        topRow.getChildren().add(titleLabel);

        String hashShort = (fileInfo.getChecksum() != null && fileInfo.getChecksum().length() >= 12)
                ? fileInfo.getChecksum().substring(0, 12) + "..."
                : (fileInfo.getChecksum() != null ? fileInfo.getChecksum() : "N/A");
        Label metaLabel = new Label("Dung lượng: " + com.peerchat.shared.util.FileUtils.formatFileSize(fileInfo.getFileSize())
                + " | SHA-256: " + hashShort);
        metaLabel.getStyleClass().add("file-card-meta");

        ProgressBar downloadBar = new ProgressBar(0.0);
        downloadBar.setPrefWidth(300);
        downloadBar.setVisible(false);
        downloadBar.setManaged(false);

        Label statusLabel = new Label("LƯU TRỮ SẴN SÀNG TRÊN MÁY CHỦ");
        statusLabel.getStyleClass().add("file-card-meta");

        Button downloadBtn = new Button("⬇ TẢI VỀ");
        downloadBtn.getStyleClass().add("file-card-btn");

        downloadBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("CHỌN NƠI LƯU TẬP TIN");
            chooser.setInitialFileName(fileInfo.getFileName());
            File dest = chooser.showSaveDialog(chatScrollPane.getScene().getWindow());

            if (dest != null) {
                downloadBtn.setDisable(true);
                downloadBtn.setText("ĐANG TẢI...");
                downloadBar.setVisible(true);
                downloadBar.setManaged(true);
                statusLabel.setText("Đang kết nối nhận dữ liệu từ máy chủ...");

                fileTransferService.requestDownload(fileInfo, dest, new FileTransferService.DownloadCallback() {
                    @Override
                    public void onProgress(double progress, long currentBytes, long totalBytes) {
                        ClientUtils.runOnFxThread(() -> {
                            downloadBar.setProgress(progress);
                            statusLabel.setText(String.format("Đã nhận: %s / %s (%.1f%%)",
                                    com.peerchat.shared.util.FileUtils.formatFileSize(currentBytes),
                                    com.peerchat.shared.util.FileUtils.formatFileSize(totalBytes),
                                    progress * 100.0));
                        });
                    }

                    @Override
                    public void onComplete(boolean success, String hash, String error) {
                        ClientUtils.runOnFxThread(() -> {
                            if (success) {
                                downloadBar.setProgress(1.0);
                                statusLabel.setText("ĐÃ TẢI XONG (Mã SHA-256 toàn vẹn 100%)");
                                statusLabel.getStyleClass().add("file-card-status-ok");
                                downloadBtn.setText("📂 MỞ THƯ MỤC");
                                downloadBtn.setDisable(false);
                                downloadBtn.setOnAction(openEvent -> openDirectory(dest.getParentFile()));
                            } else if ("FILE_NOT_FOUND".equals(error)) {
                                statusLabel.setText("[ DỮ LIỆU ĐÃ MẤT ] TẬP TIN KHÔNG CÒN TRÊN MÁY CHỦ");
                                statusLabel.getStyleClass().add("file-card-status-fail");
                                downloadBtn.setText("FILE ĐÃ MẤT");
                                downloadBtn.setDisable(true);
                            } else {
                                statusLabel.setText("LỖI TẢI VỀ: " + (error != null ? error : "Lỗi dữ liệu"));
                                statusLabel.getStyleClass().add("file-card-status-fail");
                                downloadBtn.setText("TẢI LẠI");
                                downloadBtn.setDisable(false);
                            }
                        });
                    }
                });
            }
        });

        fileCard.getChildren().addAll(topRow, metaLabel, downloadBar, statusLabel, downloadBtn);
        card.getChildren().add(fileCard);
    }

    // Mở thư mục chứa tập tin trên máy tính
    private void openDirectory(File dir) {
        try {
            if (dir != null && dir.exists()) {
                java.awt.Desktop.getDesktop().open(dir);
            }
        } catch (Exception ignored) {}
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
                    carrierStatusLabel.setText("[ MẤT LIÊN LẠC ] MÁY CHỦ ĐÃ NGẮT KẾT NỐI");
                    carrierStatusLabel.setStyle("-fx-text-fill: #b8522e; -fx-font-weight: bold;");

                    messageInputField.setDisable(true);
                    sendButton.setDisable(true);
                    attachFileButton.setDisable(true);

                    if (disconnectBanner != null) {
                        disconnectBanner.setVisible(true);
                        disconnectBanner.setManaged(true);
                    }
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
                mainSplitPane.setDividerPositions(0.13, 0.80);
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

    // Giải phóng kết nối an toàn
    private void cleanupConnection() {
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
    }

    // Quay trở về màn hình đăng nhập
    @FXML
    private void handleReturnToConnect() {
        cleanupConnection();
        ClientUtils.runOnFxThread(() -> {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/connect.fxml"));
                javafx.scene.Parent root = loader.load();

                Stage stage = (Stage) nodeCallsignLabel.getScene().getWindow();
                javafx.scene.Scene scene = new javafx.scene.Scene(root, 720, 540);
                stage.setScene(scene);
                stage.setTitle("PeerChat - Hệ thống giao tiếp và truyền tệp");
                stage.centerOnScreen();
            } catch (Exception e) {
                System.err.println("Lỗi chuyển về màn hình đăng nhập: " + e.getMessage());
            }
        });
    }

    // Ngắt kết nối có hộp thoại xác nhận và chuyển hướng về sảnh
    @FXML
    private void handleDisconnect() {
        java.util.Optional<ButtonType> result = ClientUtils.showConfirmation(
                "PeerChat - Xác nhận ngắt kết nối",
                "NGẮT LIÊN LẠC MÁY CHỦ",
                "Bạn có chắc chắn muốn ngắt kết nối và quay về màn hình đăng nhập không?"
        );
        if (result.isPresent() && result.get() == ButtonType.OK) {
            handleReturnToConnect();
        }
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

