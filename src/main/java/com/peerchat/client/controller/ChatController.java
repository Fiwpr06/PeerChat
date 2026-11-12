package com.peerchat.client.controller;

import com.peerchat.client.network.ClientSocket;
import com.peerchat.client.network.MessageReceiver;
import com.peerchat.client.network.MessageSender;
import com.peerchat.client.service.ChatService;
import com.peerchat.client.service.FileTransferService;
import com.peerchat.client.util.ClientUtils;
import com.peerchat.client.util.IconUtils;
import com.peerchat.shared.model.ClientInfo;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.model.GroupInfo;
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
import javafx.geometry.Insets;
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
import javafx.util.Pair;

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
    @FXML private ListView<GroupInfo> groupsListView;
    @FXML private Button createGroupButton;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox messagesContainer;
    @FXML private HBox disconnectBanner;
    @FXML private Button attachFileButton;
    @FXML private Button emojiButton;
    @FXML private Button recordAudioButton;
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
    private GroupInfo activeSelectedGroup = null;
    private boolean isRepoOpen = true;
    private Timeline clockTimeline;
    private final com.peerchat.client.util.AudioRecordService audioRecordService = new com.peerchat.client.util.AudioRecordService();
    private final com.peerchat.client.util.AudioPlayerService audioPlayerService = new com.peerchat.client.util.AudioPlayerService();
    private com.peerchat.client.util.EmojiPickerPopup emojiPickerPopup;
    private Timeline recordTimeline;
    private int recordDurationSec = 0;

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

        this.emojiPickerPopup = new com.peerchat.client.util.EmojiPickerPopup(emoji -> {
            int caret = messageInputField.getCaretPosition();
            messageInputField.insertText(caret, emoji);
            messageInputField.requestFocus();
        });

        if (fileTransferController != null) {
            fileTransferController.initData(fileTransferService, chatService, this::handleToggleRepo);
        }

        setupPeerDirectory();
        setupGroupDirectory();
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
            if (newVal != null) {
                activeSelectedGroup = null;
                if (groupsListView != null) {
                    groupsListView.getSelectionModel().clearSelection();
                }
                if (ProtocolConstants.TARGET_ALL.equals(newVal.getClientId())) {
                    activeSelectedPeer = null;
                    channelTargetLabel.setText("KÊNH CHUNG");
                } else {
                    activeSelectedPeer = newVal;
                    channelTargetLabel.setText("TRÒ CHUYỆN RIÊNG VỚI: " + newVal.getDisplayName() + " [" + newVal.getCoordinates() + "]");
                }
                reloadMessagesForActiveChannel();
            }
        });

        // Mặc định chọn dòng đầu tiên (Kênh chung)
        peersListView.getSelectionModel().select(0);
    }

    // Thiết lập danh sách nhóm Multicast và bộ lắng nghe tương tác
    private void setupGroupDirectory() {
        if (groupsListView == null) return;
        groupsListView.setItems(chatService.getAvailableGroups());

        // Định dạng hiển thị từng nhóm Multicast với Vector Icon và nhãn trạng thái NieR
        groupsListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(GroupInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                } else {
                    HBox cellBox = new HBox(6);
                    cellBox.setAlignment(Pos.CENTER_LEFT);

                    String myId = chatService.getClientId();
                    boolean isMember = item.hasMember(myId);
                    boolean isPending = item.hasJoinRequest(myId);
                    boolean isCreator = myId.equals(item.getCreatorId());

                    // Biểu tượng Vector: Globe (Công khai) hoặc Lock (Kín)
                    Node iconNode;
                    if (item.isPublic()) {
                        iconNode = IconUtils.createGlobeIcon(isSelected() ? IconUtils.COLOR_WHITE : IconUtils.COLOR_SLATE, 12);
                    } else {
                        iconNode = IconUtils.createLockIcon(isSelected() ? IconUtils.COLOR_WHITE : IconUtils.COLOR_OCHRE, 12);
                    }

                    Label nameLabel = new Label(item.getGroupName());
                    nameLabel.setStyle(isSelected() ? "-fx-text-fill: #ffffff; -fx-font-weight: bold;" : "-fx-text-fill: #1c1b17; -fx-font-weight: bold;");

                    Label countLabel = new Label("(" + item.getMemberCount() + ")");
                    countLabel.setStyle("-fx-text-fill: #615c4f; -fx-font-size: 10px;");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    HBox actionsBox = new HBox(3);
                    actionsBox.setAlignment(Pos.CENTER_RIGHT);

                    if (isCreator) {
                        Button manageBtn = new Button();
                        manageBtn.setGraphic(IconUtils.createGearIcon(IconUtils.COLOR_CHARCOAL, 10));
                        manageBtn.setTooltip(new Tooltip("Quản trị nhóm (Phê duyệt / Thêm / Kích)"));
                        manageBtn.getStyleClass().add("button");
                        manageBtn.setStyle("-fx-padding: 2 4;");
                        manageBtn.setOnAction(e -> {
                            e.consume();
                            openGroupManagementDialog(item);
                        });
                        actionsBox.getChildren().add(manageBtn);
                    }

                    if (isMember) {
                        if (!isCreator) {
                            Button leaveBtn = new Button("RỜI");
                            leaveBtn.getStyleClass().add("button-danger");
                            leaveBtn.setStyle("-fx-font-size: 9px; -fx-padding: 2 5;");
                            leaveBtn.setOnAction(e -> {
                                e.consume();
                                chatService.sendLeaveGroup(item.getGroupId());
                            });
                            actionsBox.getChildren().add(leaveBtn);
                        }
                    } else if (isPending) {
                        Label pendingTag = new Label("[CHỜ DUYỆT]");
                        pendingTag.setStyle("-fx-text-fill: #b8522e; -fx-font-size: 9px; -fx-font-weight: bold;");
                        actionsBox.getChildren().add(pendingTag);
                    } else {
                        Button joinBtn = new Button(item.isPublic() ? "+ VÀO" : "XIN VÀO");
                        joinBtn.getStyleClass().add("button-action");
                        joinBtn.setStyle("-fx-font-size: 9px; -fx-padding: 2 5;");
                        joinBtn.setOnAction(e -> {
                            e.consume();
                            if (item.isPublic()) {
                                chatService.sendJoinGroup(item.getGroupId());
                            } else {
                                chatService.sendRequestJoinGroup(item.getGroupId());
                            }
                        });
                        actionsBox.getChildren().add(joinBtn);
                    }

                    cellBox.getChildren().addAll(iconNode, nameLabel, countLabel, spacer, actionsBox);
                    setGraphic(cellBox);

                    // Menu ngữ cảnh chuột phải
                    ContextMenu cm = new ContextMenu();
                    if (isCreator) {
                        MenuItem manageItem = new MenuItem("Quản trị nhóm");
                        manageItem.setOnAction(ev -> openGroupManagementDialog(item));
                        cm.getItems().add(manageItem);
                    }
                    if (isMember) {
                        MenuItem leaveItem = new MenuItem("Rời nhóm");
                        leaveItem.setOnAction(ev -> chatService.sendLeaveGroup(item.getGroupId()));
                        cm.getItems().add(leaveItem);
                    } else if (isPending) {
                        MenuItem cancelItem = new MenuItem("Hủy yêu cầu xin vào");
                        cancelItem.setOnAction(ev -> chatService.sendCancelJoinRequest(item.getGroupId()));
                        cm.getItems().add(cancelItem);
                    } else {
                        MenuItem joinItem = new MenuItem(item.isPublic() ? "Tham gia nhóm" : "Xin vào nhóm");
                        joinItem.setOnAction(ev -> {
                            if (item.isPublic()) {
                                chatService.sendJoinGroup(item.getGroupId());
                            } else {
                                chatService.sendRequestJoinGroup(item.getGroupId());
                            }
                        });
                        cm.getItems().add(joinItem);
                    }
                    setContextMenu(cm);
                }
            }
        });

        // Xử lý khi chọn một nhóm Multicast để trò chuyện
        groupsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                activeSelectedGroup = newVal;
                activeSelectedPeer = null;
                peersListView.getSelectionModel().clearSelection();
                updateChannelTargetLabel();
                reloadMessagesForActiveChannel();
            }
        });

        // Lắng nghe cập nhật danh sách nhóm để đồng bộ giao diện thời gian thực
        chatService.getAvailableGroups().addListener((ListChangeListener<GroupInfo>) c -> {
            ClientUtils.runOnFxThread(() -> {
                if (activeSelectedGroup != null) {
                    GroupInfo updatedGroup = null;
                    for (GroupInfo g : chatService.getAvailableGroups()) {
                        if (g.getGroupId().equals(activeSelectedGroup.getGroupId())) {
                            updatedGroup = g;
                            break;
                        }
                    }
                    if (updatedGroup != null) {
                        activeSelectedGroup = updatedGroup;
                        updateChannelTargetLabel();
                        reloadMessagesForActiveChannel();
                    }
                }
            });
        });
    }

    // Cập nhật nhãn kênh trò chuyện trên thanh tiêu đề HUD
    private void updateChannelTargetLabel() {
        if (activeSelectedGroup != null) {
            String myId = chatService.getClientId();
            boolean isMember = activeSelectedGroup.hasMember(myId);
            boolean isPending = activeSelectedGroup.hasJoinRequest(myId);
            String modeStr = activeSelectedGroup.isPublic() ? " [CÔNG KHAI]" : " [KÍN]";
            String statusStr = isMember ? " (" + activeSelectedGroup.getMemberCount() + " thành viên)"
                    : (isPending ? " [ĐANG CHỜ DUYỆT]" : " [CHƯA THAM GIA]");
            channelTargetLabel.setText("NHÓM: #" + activeSelectedGroup.getGroupName() + modeStr + statusStr);
        } else if (activeSelectedPeer != null) {
            channelTargetLabel.setText("TRÒ CHUYỆN RIÊNG VỚI: " + activeSelectedPeer.getDisplayName() + " [" + activeSelectedPeer.getCoordinates() + "]");
        } else {
            channelTargetLabel.setText("KÊNH CHUNG");
        }
    }

    // Bật/tắt các công cụ soạn thảo tin nhắn dựa theo quyền thành viên của nhóm
    private void updateInputControlsState() {
        boolean disabled = (activeSelectedGroup != null && !activeSelectedGroup.hasMember(chatService.getClientId()));
        messageInputField.setDisable(disabled);
        sendButton.setDisable(disabled);
        attachFileButton.setDisable(disabled);
        emojiButton.setDisable(disabled);
        recordAudioButton.setDisable(disabled);

        if (disabled) {
            messageInputField.setPromptText("Nội dung được bảo vệ. Vui lòng xin vào nhóm để mở khóa trò chuyện...");
        } else {
            messageInputField.setPromptText("Soạn tin nhắn... [Enter: Gửi | Shift+Enter: Xuống dòng | Kéo thả tệp]");
        }
    }

    // Hiển thị thẻ khóa nội dung chuẩn NieR khi người dùng chưa tham gia nhóm kín
    private void renderGroupLockedCard(GroupInfo group) {
        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: #2b2923; -fx-padding: 30 20; -fx-border-color: #4b473b; -fx-border-width: 1px; -fx-max-width: 520px;");

        boolean isPublic = group.isPublic();
        boolean hasRequested = group.hasJoinRequest(chatService.getClientId());

        Node iconNode = isPublic
                ? IconUtils.createGlobeIcon(IconUtils.COLOR_SLATE, 36)
                : (hasRequested ? IconUtils.createHourglassIcon(IconUtils.COLOR_OCHRE, 36) : IconUtils.createLockIcon(IconUtils.COLOR_OCHRE, 36));

        Label titleLabel = new Label(isPublic ? "[ NHÓM CÔNG KHAI // TRUY CẬP TỰ DO ]" : "[ NHÓM KÍN // BẢO MẬT NỘI DUNG ]");
        titleLabel.setStyle("-fx-text-fill: " + (isPublic ? "#4fc3f7" : "#e68a00") + "; -fx-font-weight: bold; -fx-font-size: 13px; -fx-font-family: monospace;");

        Label nameLabel = new Label("#" + group.getGroupName());
        nameLabel.setStyle("-fx-text-fill: #cdc8b4; -fx-font-weight: bold; -fx-font-size: 18px;");

        Label descLabel;
        Button actionButton;

        if (isPublic) {
            descLabel = new Label("Nhóm này được thiết lập ở chế độ Công Khai. Bất kỳ ai cũng có thể tham gia để đọc và gửi tin nhắn.");
            descLabel.setStyle("-fx-text-fill: #a5a08f; -fx-font-size: 11px; -fx-wrap-text: true; -fx-text-alignment: center;");

            actionButton = new Button("THAM GIA NHÓM NGAY");
            actionButton.getStyleClass().add("button-action");
            actionButton.setGraphic(IconUtils.createGlobeIcon(IconUtils.COLOR_WHITE, 14));
            actionButton.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 8 20;");
            actionButton.setOnAction(e -> chatService.sendJoinGroup(group.getGroupId()));
        } else if (hasRequested) {
            descLabel = new Label("Yêu cầu xin vào nhóm của bạn đã được gửi tới Trưởng nhóm. Vui lòng chờ Trưởng nhóm phê duyệt.");
            descLabel.setStyle("-fx-text-fill: #e0d9c4; -fx-font-size: 11px; -fx-wrap-text: true; -fx-text-alignment: center;");

            actionButton = new Button("HỦY YÊU CẦU XIN VÀO");
            actionButton.getStyleClass().add("button-danger");
            actionButton.setGraphic(IconUtils.createCrossIcon(IconUtils.COLOR_WHITE, 14));
            actionButton.setStyle("-fx-font-size: 11px; -fx-padding: 8 20;");
            actionButton.setOnAction(e -> chatService.sendCancelJoinRequest(group.getGroupId()));
        } else {
            descLabel = new Label("Đây là nhóm kín. Toàn bộ nội dung trò chuyện và tệp tin đều được bảo vệ. Bạn cần gửi yêu cầu xin vào để Trưởng nhóm phê duyệt.");
            descLabel.setStyle("-fx-text-fill: #a5a08f; -fx-font-size: 11px; -fx-wrap-text: true; -fx-text-alignment: center;");

            actionButton = new Button("GỬI YÊU CẦU XIN VÀO NHÓM");
            actionButton.getStyleClass().add("button-action");
            actionButton.setGraphic(IconUtils.createMailIcon(IconUtils.COLOR_WHITE, 14));
            actionButton.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 8 20;");
            actionButton.setOnAction(e -> chatService.sendRequestJoinGroup(group.getGroupId()));
        }

        card.getChildren().addAll(iconNode, titleLabel, nameLabel, descLabel, actionButton);

        VBox centerWrapper = new VBox(card);
        centerWrapper.setAlignment(Pos.CENTER);
        centerWrapper.setStyle("-fx-padding: 40 10;");
        messagesContainer.getChildren().add(centerWrapper);
    }

    // Mở hộp thoại Quản lý nhóm (Chỉ Admin / Người tạo nhóm mới có quyền)
    private void openGroupManagementDialog(GroupInfo targetGroup) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("PeerChat - Quản trị nhóm");
        dialog.setHeaderText("QUẢN TRỊ NHÓM MULTICAST: #" + targetGroup.getGroupName());

        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {}

        ButtonType closeButton = new ButtonType("ĐÓNG", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeButton);

        VBox contentBox = new VBox(10);
        contentBox.setPrefWidth(460);
        contentBox.setPrefHeight(480);

        // Hàng điều khiển chế độ Công khai / Kín
        HBox modeBar = new HBox(10);
        modeBar.setAlignment(Pos.CENTER_LEFT);
        modeBar.setStyle("-fx-background-color: #2b2923; -fx-padding: 8 10; -fx-border-color: #4b473b; -fx-border-width: 1px;");

        Label modeLabel = new Label("CHẾ ĐỘ NHÓM:");
        modeLabel.setStyle("-fx-text-fill: #a5a08f; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label modeStatus = new Label(targetGroup.isPublic() ? "CÔNG KHAI (Ai cũng có thể vào)" : "KÍN (Cần Admin duyệt)");
        modeStatus.setStyle("-fx-text-fill: " + (targetGroup.isPublic() ? "#4fc3f7" : "#e68a00") + "; -fx-font-weight: bold; -fx-font-size: 11px;");

        Region modeSpacer = new Region();
        HBox.setHgrow(modeSpacer, Priority.ALWAYS);

        Button toggleModeBtn = new Button(targetGroup.isPublic() ? "CHUYỂN SANG KÍN" : "CHUYỂN SANG CÔNG KHAI");
        toggleModeBtn.getStyleClass().add("button");
        toggleModeBtn.setGraphic(targetGroup.isPublic()
                ? IconUtils.createLockIcon(IconUtils.COLOR_OCHRE, 12)
                : IconUtils.createGlobeIcon(IconUtils.COLOR_SLATE, 12));
        toggleModeBtn.setStyle("-fx-font-size: 10px; -fx-padding: 3 8;");
        modeBar.getChildren().addAll(modeLabel, modeStatus, modeSpacer, toggleModeBtn);

        // Mục 1: Yêu cầu chờ duyệt
        Label pendingHeader = new Label("[ YÊU CẦU CHỜ DUYỆT (0) ]");
        pendingHeader.getStyleClass().add("panel-header");

        ListView<String> pendingListView = new ListView<>();
        pendingListView.setPrefHeight(100);

        // Mục 2: Thành viên trong nhóm
        Label membersHeader = new Label("[ THÀNH VIÊN TRONG NHÓM ]");
        membersHeader.getStyleClass().add("panel-header");

        ListView<String> membersListView = new ListView<>();
        membersListView.setPrefHeight(120);

        // Mục 3: Thêm người dùng trực tuyến
        Label nonMembersHeader = new Label("[ THÊM NGƯỜI DÙNG ĐANG TRỰC TUYẾN ]");
        nonMembersHeader.getStyleClass().add("panel-header");

        ListView<ClientInfo> nonMembersListView = new ListView<>();
        nonMembersListView.setPrefHeight(120);

        Runnable refreshLists = () -> {
            GroupInfo currentGroup = null;
            for (GroupInfo g : chatService.getAvailableGroups()) {
                if (g.getGroupId().equals(targetGroup.getGroupId())) {
                    currentGroup = g;
                    break;
                }
            }
            if (currentGroup == null) {
                dialog.close();
                return;
            }

            final GroupInfo finalGroup = currentGroup;
            boolean isPub = finalGroup.isPublic();
            modeStatus.setText(isPub ? "CÔNG KHAI (Ai cũng có thể vào)" : "KÍN (Cần Admin duyệt)");
            modeStatus.setStyle("-fx-text-fill: " + (isPub ? "#4fc3f7" : "#e68a00") + "; -fx-font-weight: bold; -fx-font-size: 11px;");
            toggleModeBtn.setText(isPub ? "CHUYỂN SANG KÍN" : "CHUYỂN SANG CÔNG KHAI");
            toggleModeBtn.setGraphic(isPub
                    ? IconUtils.createLockIcon(IconUtils.COLOR_OCHRE, 12)
                    : IconUtils.createGlobeIcon(IconUtils.COLOR_SLATE, 12));
            toggleModeBtn.setOnAction(e -> chatService.sendToggleGroupPublic(finalGroup.getGroupId(), !isPub));

            pendingHeader.setText("[ YÊU CẦU CHỜ DUYỆT (" + finalGroup.getPendingCount() + ") ]");
            pendingListView.getItems().setAll(finalGroup.getPendingMemberIds());

            membersHeader.setText("[ THÀNH VIÊN TRONG NHÓM (" + finalGroup.getMemberCount() + ") ]");
            membersListView.getItems().setAll(finalGroup.getMemberIds());

            ObservableList<ClientInfo> nonMembers = FXCollections.observableArrayList();
            for (ClientInfo peer : chatService.getOnlinePeers()) {
                if (!finalGroup.hasMember(peer.getClientId()) && !finalGroup.hasJoinRequest(peer.getClientId())) {
                    nonMembers.add(peer);
                }
            }
            nonMembersListView.getItems().setAll(nonMembers);
        };

        pendingListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String requesterId, boolean empty) {
                super.updateItem(requesterId, empty);
                if (empty || requesterId == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);

                    String name = requesterId;
                    for (ClientInfo p : chatService.getOnlinePeers()) {
                        if (p.getClientId().equals(requesterId)) {
                            name = p.getDisplayName() + " [" + p.getCoordinates() + "]";
                            break;
                        }
                    }

                    Label nameLbl = new Label(name);
                    nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #cdc8b4;");

                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    Button approveBtn = new Button("DUYỆT");
                    approveBtn.getStyleClass().add("button-action");
                    approveBtn.setGraphic(IconUtils.createCheckIcon(IconUtils.COLOR_WHITE, 10));
                    approveBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6;");
                    approveBtn.setOnAction(e -> chatService.sendApproveJoinRequest(targetGroup.getGroupId(), requesterId));

                    Button rejectBtn = new Button("TỪ CHỐI");
                    rejectBtn.getStyleClass().add("button-danger");
                    rejectBtn.setGraphic(IconUtils.createCrossIcon(IconUtils.COLOR_WHITE, 10));
                    rejectBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6;");
                    rejectBtn.setOnAction(e -> chatService.sendRejectJoinRequest(targetGroup.getGroupId(), requesterId));

                    box.getChildren().addAll(nameLbl, sp, approveBtn, rejectBtn);
                    setGraphic(box);
                }
            }
        });

        membersListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String memberId, boolean empty) {
                super.updateItem(memberId, empty);
                if (empty || memberId == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);

                    String name = memberId;
                    if (memberId.equals(chatService.getClientId())) {
                        name = chatService.getDisplayName() + " (Bạn)";
                    } else {
                        for (ClientInfo p : chatService.getOnlinePeers()) {
                            if (p.getClientId().equals(memberId)) {
                                name = p.getDisplayName() + " [" + p.getCoordinates() + "]";
                                break;
                            }
                        }
                    }

                    boolean isCreator = memberId.equals(targetGroup.getCreatorId());
                    Label nameLbl = new Label(name + (isCreator ? " [ADMIN]" : ""));
                    nameLbl.setStyle(isCreator ? "-fx-font-weight: bold; -fx-text-fill: #1f4f6e;" : "-fx-font-weight: bold;");

                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    box.getChildren().addAll(nameLbl, sp);

                    if (!isCreator) {
                        Button kickBtn = new Button("KÍCH");
                        kickBtn.setGraphic(IconUtils.createUserMinusIcon(IconUtils.COLOR_WHITE, 10));
                        kickBtn.getStyleClass().add("button-danger");
                        kickBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6;");
                        kickBtn.setOnAction(e -> chatService.sendKickUserFromGroup(targetGroup.getGroupId(), memberId));
                        box.getChildren().add(kickBtn);
                    }

                    setGraphic(box);
                }
            }
        });

        nonMembersListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ClientInfo peer, boolean empty) {
                super.updateItem(peer, empty);
                if (empty || peer == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);

                    Label nameLbl = new Label(peer.getDisplayName() + " [" + peer.getCoordinates() + "]");
                    nameLbl.setStyle("-fx-font-weight: bold;");

                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    Button addBtn = new Button("+ THÊM");
                    addBtn.setGraphic(IconUtils.createUserPlusIcon(IconUtils.COLOR_WHITE, 10));
                    addBtn.getStyleClass().add("button-action");
                    addBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6;");
                    addBtn.setOnAction(e -> chatService.sendAddUserToGroup(targetGroup.getGroupId(), peer.getClientId()));

                    box.getChildren().addAll(nameLbl, sp, addBtn);
                    setGraphic(box);
                }
            }
        });

        ListChangeListener<GroupInfo> groupChangeListener = c -> ClientUtils.runOnFxThread(refreshLists);
        chatService.getAvailableGroups().addListener(groupChangeListener);

        ListChangeListener<ClientInfo> peersChangeListener = c -> ClientUtils.runOnFxThread(refreshLists);
        chatService.getOnlinePeers().addListener(peersChangeListener);

        dialog.setOnHidden(e -> {
            chatService.getAvailableGroups().removeListener(groupChangeListener);
            chatService.getOnlinePeers().removeListener(peersChangeListener);
        });

        refreshLists.run();

        contentBox.getChildren().addAll(modeBar, pendingHeader, pendingListView, membersHeader, membersListView, nonMembersHeader, nonMembersListView);
        dialog.getDialogPane().setContent(contentBox);
        dialog.showAndWait();
    }

    // Mở hộp thoại tạo nhóm Multicast mới (Hỗ trợ cấu hình Công khai / Kín)
    @FXML
    private void handleCreateGroup() {
        Dialog<Pair<String, Boolean>> dialog = new Dialog<>();
        dialog.setTitle("PeerChat - Tạo nhóm Multicast");
        dialog.setHeaderText("TẠO NHÓM MULTICAST QUA TCP");

        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {}

        ButtonType createButtonType = new ButtonType("TẠO NHÓM", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        VBox content = new VBox(12);
        content.setPrefWidth(380);
        content.setPadding(new Insets(10));

        Label namePrompt = new Label("Tên nhóm:");
        namePrompt.setStyle("-fx-font-weight: bold;");

        TextField nameField = new TextField("Nhóm-" + (chatService.getAvailableGroups().size() + 1));
        nameField.setStyle("-fx-background-color: #2b2923; -fx-text-fill: #cdc8b4; -fx-border-color: #8c8675;");

        CheckBox publicCheckBox = new CheckBox("Nhóm Công Khai (Cho phép mọi người tự do tham gia không cần duyệt)");
        publicCheckBox.setStyle("-fx-font-size: 11px; -fx-text-fill: #1c1b17;");
        publicCheckBox.setSelected(false); // Mặc định là nhóm kín cần xét duyệt

        content.getChildren().addAll(namePrompt, nameField, publicCheckBox);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                return new Pair<>(nameField.getText(), publicCheckBox.isSelected());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            String name = result.getKey();
            boolean isPublic = result.getValue();
            if (name != null && !name.trim().isEmpty()) {
                chatService.sendCreateGroup(name.trim(), isPublic);
            }
        });
    }

    // Tái cấu trúc danh sách hiển thị với Kênh Chung luôn ở dòng đầu
    private void rebuildDisplayPeers() {
        ClientInfo currentSelected = peersListView.getSelectionModel().getSelectedItem();
        displayPeers.clear();
        displayPeers.add(CHANNEL_BROADCAST);
        displayPeers.addAll(chatService.getOnlinePeers());

        if (currentSelected != null && !ProtocolConstants.TARGET_ALL.equals(currentSelected.getClientId())) {
            peersListView.getSelectionModel().select(currentSelected);
        } else if (activeSelectedGroup == null) {
            peersListView.getSelectionModel().select(0);
        }
    }

    // Tải lại các tin nhắn tương ứng với kênh đang mở (Kênh chung, Nhóm hoặc Chat riêng)
    private void reloadMessagesForActiveChannel() {
        messagesContainer.getChildren().clear();
        updateInputControlsState();

        if (activeSelectedGroup != null && !activeSelectedGroup.hasMember(chatService.getClientId())) {
            renderGroupLockedCard(activeSelectedGroup);
            return;
        }

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
        if (activeSelectedGroup != null) {
            // Đang mở kênh nhóm: chỉ hiển thị tin nhắn gửi đến nhóm này
            return activeSelectedGroup.getGroupId().equals(msg.getTargetId());
        } else if (activeSelectedPeer == null) {
            // Kênh chung: chỉ nhận các tin nhắn gửi tới ALL (không nhận tin nhóm hay tin riêng)
            return !msg.isDirect() && (msg.getTargetId() == null || ProtocolConstants.TARGET_ALL.equalsIgnoreCase(msg.getTargetId()));
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

    // Bật/tắt bảng chọn Emoji cảm xúc
    @FXML
    private void handleToggleEmoji() {
        if (emojiPickerPopup != null) {
            emojiPickerPopup.show(emojiButton);
        }
    }

    // Bật/tắt chế độ ghi âm giọng nói qua Microphone
    @FXML
    private void handleToggleRecordAudio() {
        if (!audioRecordService.isRecording()) {
            boolean started = audioRecordService.startRecording();
            if (started) {
                recordDurationSec = 0;
                recordAudioButton.setText("■ DỪNG (0s)");
                recordAudioButton.setStyle("-fx-background-color: #b8522e; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
                recordTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                    recordDurationSec++;
                    recordAudioButton.setText("■ DỪNG (" + recordDurationSec + "s)");
                }));
                recordTimeline.setCycleCount(Animation.INDEFINITE);
                recordTimeline.play();
            } else {
                ClientUtils.showError(
                        "LỖI MICROPHONE",
                        "Không thể truy cập Microphone",
                        "Vui lòng kiểm tra thiết bị thu âm trên máy tính."
                );
            }
        } else {
            if (recordTimeline != null) {
                recordTimeline.stop();
            }
            recordAudioButton.setText("🎙 THOẠI");
            recordAudioButton.setStyle("");

            File wavFile = audioRecordService.stopRecording();
            if (wavFile != null && wavFile.exists() && wavFile.length() > 0) {
                stageAndSendDroppedFile(wavFile);
            }
        }
    }

    // Đẩy tập tin kéo thả lên server để chia sẻ vào cuộc trò chuyện đang chọn
    private void stageAndSendDroppedFile(File file) {
        if (file != null && file.exists() && file.isFile()) {
            if (activeSelectedGroup != null && !activeSelectedGroup.hasMember(chatService.getClientId())) {
                ClientUtils.showError("TRUY CẬP BỊ TỪ CHỐI", "Không thể gửi tệp", "Bạn chưa phải là thành viên của nhóm này.");
                return;
            }
            if (FileTransferController.isImageFile(file.getName())) {
                try {
                    imageCache.put(file.getName(), new Image(file.toURI().toString()));
                } catch (Exception ignored) {}
            }
            String targetId;
            if (activeSelectedGroup != null) {
                targetId = activeSelectedGroup.getGroupId();
            } else if (activeSelectedPeer != null) {
                targetId = activeSelectedPeer.getClientId();
            } else {
                targetId = ProtocolConstants.TARGET_ALL;
            }
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

        Label targetTag;
        if (msg.getTargetId() != null && msg.getTargetId().startsWith("grp_")) {
            targetTag = new Label("[NHÓM]");
        } else {
            targetTag = new Label(msg.isDirect() ? "[RIÊNG]" : "[CHUNG]");
        }
        targetTag.setStyle("-fx-text-fill: #615c4f; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label timeLabel = new Label(msg.getFormattedTime());
        timeLabel.getStyleClass().add("message-timestamp");

        header.getChildren().addAll(senderLabel, targetTag, timeLabel);
        card.getChildren().add(header);

        if (msg.isFileMessage() && msg.getFileInfo() != null) {
            FileInfo fileInfo = msg.getFileInfo();
            if (FileTransferController.isImageFile(fileInfo.getFileName())) {
                renderImageCard(card, fileInfo);
            } else if (isAudioFile(fileInfo.getFileName())) {
                renderAudioCard(card, fileInfo);
            } else if (isVideoFile(fileInfo.getFileName())) {
                renderVideoCard(card, fileInfo);
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

    // Kiểm tra tập tin có phải âm thanh không
    public static boolean isAudioFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".ogg") || lower.endsWith(".m4a");
    }

    // Kiểm tra tập tin có phải video không
    public static boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".webm");
    }

    // Hiển thị thẻ tin nhắn âm thanh Voice Note
    private void renderAudioCard(VBox card, FileInfo fileInfo) {
        VBox audioCard = new VBox(6);
        audioCard.getStyleClass().add("file-card");
        audioCard.setStyle("-fx-border-color: #8c8675; -fx-background-color: #dfdbcc; -fx-padding: 8px;");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label("🎙");
        iconLabel.setStyle("-fx-font-size: 16px;");

        Label titleLabel = new Label("VOICE NOTE // " + fileInfo.getFileName());
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1c1b17; -fx-font-size: 11px;");

        topRow.getChildren().addAll(iconLabel, titleLabel);

        Label metaLabel = new Label("Dung lượng: " + com.peerchat.shared.util.FileUtils.formatFileSize(fileInfo.getFileSize()) +
                " | Định dạng: WAV Audio");
        metaLabel.getStyleClass().add("file-card-meta");

        HBox actionsRow = new HBox(8);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button playBtn = new Button("▶ PHÁT ÂM THANH");
        playBtn.getStyleClass().add("button-action");
        playBtn.setStyle("-fx-font-size: 10px; -fx-padding: 4 10;");

        Button downloadBtn = new Button("⬇ TẢI VỀ");
        downloadBtn.getStyleClass().add("file-card-btn");
        downloadBtn.setStyle("-fx-font-size: 10px; -fx-padding: 4 10;");

        File cacheFile = new File(CACHE_DIR, fileInfo.getFileId() + "_" + fileInfo.getFileName());

        playBtn.setOnAction(e -> {
            if (audioPlayerService.isPlaying(cacheFile)) {
                audioPlayerService.stop();
                playBtn.setText("▶ PHÁT ÂM THANH");
                return;
            }

            if (cacheFile.exists() && cacheFile.length() > 0) {
                playBtn.setText("■ DỪNG");
                boolean playing = audioPlayerService.play(cacheFile, () -> {
                    playBtn.setText("▶ PHÁT ÂM THANH");
                });
                if (!playing) {
                    playBtn.setText("LỖI PHÁT");
                }
            } else {
                playBtn.setText("⏳ ĐANG TẢI...");
                playBtn.setDisable(true);
                fileTransferService.requestDownload(fileInfo, cacheFile, new FileTransferService.DownloadCallback() {
                    @Override
                    public void onProgress(double progress, long currentBytes, long totalBytes) {}

                    @Override
                    public void onComplete(boolean success, String hash, String error) {
                        ClientUtils.runOnFxThread(() -> {
                            playBtn.setDisable(false);
                            if (success && cacheFile.exists()) {
                                playBtn.setText("■ DỪNG");
                                audioPlayerService.play(cacheFile, () -> playBtn.setText("▶ PHÁT ÂM THANH"));
                            } else {
                                playBtn.setText("❌ LỖI TẢI");
                            }
                        });
                    }
                });
            }
        });

        downloadBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("LƯU FILE ÂM THANH");
            chooser.setInitialFileName(fileInfo.getFileName());
            File dest = chooser.showSaveDialog(chatScrollPane.getScene().getWindow());
            if (dest != null) {
                if (cacheFile.exists()) {
                    try {
                        java.nio.file.Files.copy(cacheFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        downloadBtn.setText("📂 MỞ THƯ MỤC");
                        downloadBtn.setOnAction(ev -> openDirectory(dest.getParentFile()));
                    } catch (Exception ex) {
                        downloadBtn.setText("LỖI LƯU");
                    }
                } else {
                    fileTransferService.requestDownload(fileInfo, dest, new FileTransferService.DownloadCallback() {
                        @Override
                        public void onProgress(double progress, long currentBytes, long totalBytes) {}

                        @Override
                        public void onComplete(boolean success, String hash, String error) {
                            ClientUtils.runOnFxThread(() -> {
                                if (success) {
                                    downloadBtn.setText("📂 MỞ THƯ MỤC");
                                    downloadBtn.setOnAction(ev -> openDirectory(dest.getParentFile()));
                                }
                            });
                        }
                    });
                }
            }
        });

        actionsRow.getChildren().addAll(playBtn, downloadBtn);
        audioCard.getChildren().addAll(topRow, metaLabel, actionsRow);
        card.getChildren().add(audioCard);
    }

    // Hiển thị thẻ tập tin Video
    private void renderVideoCard(VBox card, FileInfo fileInfo) {
        VBox videoCard = new VBox(6);
        videoCard.getStyleClass().add("file-card");
        videoCard.setStyle("-fx-border-color: #8c8675; -fx-background-color: #dfdbcc; -fx-padding: 8px;");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label("🎬");
        iconLabel.setStyle("-fx-font-size: 16px;");

        Label titleLabel = new Label("VIDEO // " + fileInfo.getFileName());
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1c1b17; -fx-font-size: 11px;");

        topRow.getChildren().addAll(iconLabel, titleLabel);

        Label metaLabel = new Label("Dung lượng: " + com.peerchat.shared.util.FileUtils.formatFileSize(fileInfo.getFileSize()) +
                " | SHA-256: " + ((fileInfo.getChecksum() != null && fileInfo.getChecksum().length() >= 10) ? fileInfo.getChecksum().substring(0, 10) + "..." : "N/A"));
        metaLabel.getStyleClass().add("file-card-meta");

        HBox actionsRow = new HBox(8);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button openVideoBtn = new Button("▶ MỞ XEM VIDEO");
        openVideoBtn.getStyleClass().add("button-action");
        openVideoBtn.setStyle("-fx-font-size: 10px; -fx-padding: 4 10;");

        Button downloadBtn = new Button("⬇ TẢI VỀ");
        downloadBtn.getStyleClass().add("file-card-btn");
        downloadBtn.setStyle("-fx-font-size: 10px; -fx-padding: 4 10;");

        File cacheFile = new File(CACHE_DIR, fileInfo.getFileId() + "_" + fileInfo.getFileName());

        openVideoBtn.setOnAction(e -> {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    java.awt.Desktop.getDesktop().open(cacheFile);
                } catch (Exception ex) {
                    openVideoBtn.setText("LỖI MỞ");
                }
            } else {
                openVideoBtn.setText("⏳ ĐANG TẢI...");
                openVideoBtn.setDisable(true);
                fileTransferService.requestDownload(fileInfo, cacheFile, new FileTransferService.DownloadCallback() {
                    @Override
                    public void onProgress(double progress, long currentBytes, long totalBytes) {
                        ClientUtils.runOnFxThread(() -> {
                            openVideoBtn.setText(String.format("⏳ TẢI (%.0f%%)", progress * 100.0));
                        });
                    }

                    @Override
                    public void onComplete(boolean success, String hash, String error) {
                        ClientUtils.runOnFxThread(() -> {
                            openVideoBtn.setDisable(false);
                            if (success && cacheFile.exists()) {
                                openVideoBtn.setText("▶ MỞ XEM VIDEO");
                                try {
                                    java.awt.Desktop.getDesktop().open(cacheFile);
                                } catch (Exception ex) {
                                    openVideoBtn.setText("LỖI MỞ");
                                }
                            } else {
                                openVideoBtn.setText("❌ LỖI TẢI");
                            }
                        });
                    }
                });
            }
        });

        downloadBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("LƯU VIDEO");
            chooser.setInitialFileName(fileInfo.getFileName());
            File dest = chooser.showSaveDialog(chatScrollPane.getScene().getWindow());
            if (dest != null) {
                if (cacheFile.exists()) {
                    try {
                        java.nio.file.Files.copy(cacheFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        downloadBtn.setText("📂 MỞ THƯ MỤC");
                        downloadBtn.setOnAction(ev -> openDirectory(dest.getParentFile()));
                    } catch (Exception ex) {
                        downloadBtn.setText("LỖI LƯU");
                    }
                } else {
                    fileTransferService.requestDownload(fileInfo, dest, new FileTransferService.DownloadCallback() {
                        @Override
                        public void onProgress(double progress, long currentBytes, long totalBytes) {}

                        @Override
                        public void onComplete(boolean success, String hash, String error) {
                            ClientUtils.runOnFxThread(() -> {
                                if (success) {
                                    downloadBtn.setText("📂 MỞ THƯ MỤC");
                                    downloadBtn.setOnAction(ev -> openDirectory(dest.getParentFile()));
                                }
                            });
                        }
                    });
                }
            }
        });

        actionsRow.getChildren().addAll(openVideoBtn, downloadBtn);
        videoCard.getChildren().addAll(topRow, metaLabel, actionsRow);
        card.getChildren().add(videoCard);
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
                    case GROUP_LIST_UPDATE -> {
                        List<GroupInfo> groups = GroupInfo.listFromJson(message.getPayloadAsText());
                        chatService.updateGroupList(groups);
                        if (activeSelectedGroup != null) {
                            boolean stillMember = false;
                            for (GroupInfo g : groups) {
                                if (g.getGroupId().equals(activeSelectedGroup.getGroupId())) {
                                    activeSelectedGroup = g;
                                    stillMember = true;
                                    ClientUtils.runOnFxThread(() ->
                                        channelTargetLabel.setText("NHÓM: #" + g.getGroupName() + " (" + g.getMemberCount() + " thành viên)")
                                    );
                                    break;
                                }
                            }
                            if (!stillMember) {
                                ClientUtils.runOnFxThread(() -> {
                                    activeSelectedGroup = null;
                                    peersListView.getSelectionModel().select(0);
                                });
                            }
                        }
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
                mainSplitPane.setDividerPositions(0.16, 0.80);
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

        String targetId;
        if (activeSelectedGroup != null) {
            if (!activeSelectedGroup.hasMember(chatService.getClientId())) {
                ClientUtils.showError("TRUY CẬP BỊ TỪ CHỐI", "Không thể gửi tin nhắn", "Bạn chưa phải là thành viên của nhóm này.");
                return;
            }
            targetId = activeSelectedGroup.getGroupId();
        } else if (activeSelectedPeer != null) {
            targetId = activeSelectedPeer.getClientId();
        } else {
            targetId = ProtocolConstants.TARGET_ALL;
        }
        chatService.sendMessage(content, targetId);
        messageInputField.clear();
        messageInputField.requestFocus();
    }

    // Giải phóng kết nối an toàn
    private void cleanupConnection() {
        audioPlayerService.stop();
        if (audioRecordService.isRecording()) {
            audioRecordService.stopRecording();
        }
        if (recordTimeline != null) {
            recordTimeline.stop();
        }
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

