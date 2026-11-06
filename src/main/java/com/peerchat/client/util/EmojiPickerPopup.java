package com.peerchat.client.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.function.Consumer;

// Bảng chọn Emoji cảm xúc dạng Popup giao diện NieR:Automata (Auto-hide khi click ra ngoài)
public class EmojiPickerPopup {

    // Danh sách emoji thông dụng phân loại theo nhóm
    private static final String[] EMOJIS = {
            // Cảm xúc & Mặt cười
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
            "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
            "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧",
            "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐",
            "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😦", "😧",
            "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓",
            "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀",

            // Bàn tay & Cử chỉ
            "👍", "👎", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
            "🤳", "💪", "👈", "👉", "👆", "👇", "✌️", "🤞", "🤟", "🤘",
            "🤙", "👌", "🤌", "🤏", "🖐", "✋", "🖖", "👊", "🤛", "🤜",
            "👋", "👀", "👁️", "🧠", "👄", "💋",

            // Trái tim & Tình cảm
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "💌",

            // Biểu tượng vui & Vật phẩm
            "🔥", "✨", "🌟", "💫", "💥", "💯", "🎉", "🎊", "🎁", "🏆",
            "🥇", "🎈", "🚀", "💡", "☕", "🍺", "🍕", "🍔", "🐱", "🐶",
            "🦄", "🤖", "👻", "💩"
    };

    private final Popup popup;
    private final Consumer<String> onSelectCallback;

    public EmojiPickerPopup(Consumer<String> onSelectCallback) {
        this.onSelectCallback = onSelectCallback;
        this.popup = new Popup();
        this.popup.setAutoHide(true);
        this.popup.setHideOnEscape(true);

        VBox container = buildUi();
        this.popup.getContent().add(container);
    }

    private VBox buildUi() {
        VBox root = new VBox(6);
        root.setPrefWidth(330);
        root.setMaxWidth(330);
        root.setPrefHeight(260);
        root.setStyle(
                "-fx-background-color: #cdc8b4; " +
                "-fx-border-color: #4b473b; " +
                "-fx-border-width: 2px; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 8, 0, 0, 3);"
        );
        root.setPadding(new Insets(8));

        // Thanh tiêu đề Popup
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-border-color: transparent transparent #8c8675 transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 6 0;");

        Label title = new Label("BIỂU TƯỢNG CẢM XÚC [EMOJI]");
        title.setStyle("-fx-font-family: 'Consolas', 'Segoe UI', monospace; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1c1b17;");

        header.getChildren().add(title);

        // Khung cuộn chứa các nút Emoji
        FlowPane flow = new FlowPane();
        flow.setHgap(4);
        flow.setVgap(4);
        flow.setPrefWrapLength(300);
        flow.setPadding(new Insets(4));
        flow.setStyle("-fx-background-color: #dfdbcc;");

        for (String emoji : EMOJIS) {
            Button btn = new Button(emoji);
            btn.setPrefSize(34, 34);
            btn.setMinSize(34, 34);
            btn.setMaxSize(34, 34);
            btn.setStyle(
                    "-fx-background-color: transparent; " +
                    "-fx-font-size: 16px; " +
                    "-fx-padding: 0; " +
                    "-fx-cursor: hand;"
            );

            // Hiệu ứng rê chuột phong cách Inverted Block Hover của NieR
            btn.setOnMouseEntered(e -> btn.setStyle(
                    "-fx-background-color: #35332b; " +
                    "-fx-font-size: 16px; " +
                    "-fx-padding: 0; " +
                    "-fx-cursor: hand; " +
                    "-fx-text-fill: #ffffff;"
            ));
            btn.setOnMouseExited(e -> btn.setStyle(
                    "-fx-background-color: transparent; " +
                    "-fx-font-size: 16px; " +
                    "-fx-padding: 0; " +
                    "-fx-cursor: hand;"
            ));

            btn.setOnAction(e -> {
                if (onSelectCallback != null) {
                    onSelectCallback.accept(emoji);
                }
                popup.hide();
            });

            flow.getChildren().add(btn);
        }

        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: #dfdbcc; -fx-background-color: transparent; -fx-border-color: #8c8675;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(header, scroll);
        return root;
    }

    public void show(Node anchor) {
        if (anchor == null || popup.isShowing()) {
            popup.hide();
            return;
        }

        Window window = anchor.getScene().getWindow();
        javafx.geometry.Point2D point = anchor.localToScreen(0, 0);

        if (point != null) {
            // Hiển thị phía trên nút bấm anchor
            double popupHeight = 260;
            double x = point.getX();
            double y = point.getY() - popupHeight - 6;
            if (y < 0) {
                y = point.getY() + anchor.getBoundsInLocal().getHeight() + 6;
            }
            popup.show(window, x, y);
        }
    }

    public void hide() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }
}
