package com.peerchat.client.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

// Tiện ích vẽ biểu tượng Vector (SVGPath) thuần JavaFX
// Thay thế hoàn toàn icon/emoji của hệ điều hành, chống lỗi font và đảm bảo đồng bộ phong cách NieR:Automata
public final class IconUtils {

    // Màu sắc quy chuẩn giao diện NieR:Automata YoRHa OS
    public static final String COLOR_OCHRE = "#b8522e";        // Điểm nhấn Rust Ochre
    public static final String COLOR_SLATE = "#1f4f6e";        // Xanh đá phiến
    public static final String COLOR_CHARCOAL = "#35332b";     // Đen than chì NieR
    public static final String COLOR_MUTED = "#736e60";        // Xám rêu trung tính
    public static final String COLOR_WHITE = "#ffffff";        // Trắng sáng
    public static final String COLOR_SUCCESS = "#2e7d32";      // Xanh lá thành công

    // Đường dẫn Vector SVG chuẩn (viewBox 24x24)
    private static final String SVG_LOCK = "M12 2a5 5 0 0 0-5 5v3H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2h-1V7a5 5 0 0 0-5-5zm-3 5a3 3 0 0 1 6 0v3H9V7zm3 6a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z";
    private static final String SVG_GLOBE = "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm7.93 9h-3.18a15.8 15.8 0 0 0-1.38-5.38A8.03 8.03 0 0 1 19.93 11zM12 4.07a14 14 0 0 1 1.77 6.93H10.23A14 14 0 0 1 12 4.07zM4.07 13h3.18a15.8 15.8 0 0 0 1.38 5.38A8.03 8.03 0 0 1 4.07 13zm3.18-2H4.07a8.03 8.03 0 0 1 4.56-5.38A15.8 15.8 0 0 0 7.25 11zm4.75 8.93A14 14 0 0 1 10.23 13h3.54a14 14 0 0 1-1.77 6.93zm2.63-3.55a15.8 15.8 0 0 0 1.38-5.38h3.18a8.03 8.03 0 0 1-4.56 5.38z";
    private static final String SVG_GEAR = "M19.4 13a7.8 7.8 0 0 0 .1-1 7.8 7.8 0 0 0-.1-1l2.1-1.6a.5.5 0 0 0 .1-.6l-2-3.5a.5.5 0 0 0-.6-.2l-2.5 1a7.4 7.4 0 0 0-1.7-1l-.4-2.6a.5.5 0 0 0-.5-.5h-4a.5.5 0 0 0-.5.5l-.4 2.6a7.4 7.4 0 0 0-1.7 1l-2.5-1a.5.5 0 0 0-.6.2l-2 3.5a.5.5 0 0 0 .1.6l2.1 1.6a7.8 7.8 0 0 0-.1 1 7.8 7.8 0 0 0 .1 1l-2.1 1.6a.5.5 0 0 0-.1.6l2 3.5a.5.5 0 0 0 .6.2l2.5-1a7.4 7.4 0 0 0 1.7 1l.4 2.6a.5.5 0 0 0 .5.5h4a.5.5 0 0 0 .5-.5l.4-2.6a7.4 7.4 0 0 0 1.7-1l2.5 1a.5.5 0 0 0 .6-.2l2-3.5a.5.5 0 0 0-.1-.6l-2.1-1.6zM12 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7z";
    private static final String SVG_CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z";
    private static final String SVG_CROSS = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12 19 6.41z";
    private static final String SVG_MAIL = "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z";
    private static final String SVG_EXIT = "M10.09 15.59L11.5 17l5-5-5-5-1.41 1.41L12.67 11H3v2h9.67l-2.58 2.59zM19 3H5a2 2 0 0 0-2 2v4h2V5h14v14H5v-4H3v4a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2z";
    private static final String SVG_HOURGLASS = "M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6zm10 14.5V20H8v-3.5l4-4 4 4zM12 11.5l-4-4V4h8v3.5l-4 4z";
    private static final String SVG_USER_PLUS = "M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm-9-2V7H4v3H1v2h3v3h2v-3h3v-2H6zm9 4c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    private static final String SVG_USER_MINUS = "M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm6 0v2h-8v-2h8zm-6 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";

    private IconUtils() {}

    // Tạo một Node chứa biểu tượng SVGPath với kích thước và màu sắc chuẩn xác
    public static Node createIcon(String svgContent, String hexColor, double size) {
        SVGPath svg = new SVGPath();
        svg.setContent(svgContent);
        svg.setFill(Color.web(hexColor != null ? hexColor : COLOR_CHARCOAL));

        StackPane container = new StackPane(svg);
        container.setMinSize(size, size);
        container.setPrefSize(size, size);
        container.setMaxSize(size, size);
        container.setAlignment(Pos.CENTER);

        // Vector gốc được vẽ trên khung 24x24 pixel
        double scale = size / 24.0;
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        return container;
    }

    // Biểu tượng Khóa (Nhóm kín)
    public static Node createLockIcon(String hexColor, double size) {
        return createIcon(SVG_LOCK, hexColor, size);
    }

    // Biểu tượng Địa cầu (Nhóm công khai)
    public static Node createGlobeIcon(String hexColor, double size) {
        return createIcon(SVG_GLOBE, hexColor, size);
    }

    // Biểu tượng Bánh răng (Quản trị nhóm)
    public static Node createGearIcon(String hexColor, double size) {
        return createIcon(SVG_GEAR, hexColor, size);
    }

    // Biểu tượng Dấu tích (Chấp thuận duyệt)
    public static Node createCheckIcon(String hexColor, double size) {
        return createIcon(SVG_CHECK, hexColor, size);
    }

    // Biểu tượng Dấu chéo (Từ chối / Hủy)
    public static Node createCrossIcon(String hexColor, double size) {
        return createIcon(SVG_CROSS, hexColor, size);
    }

    // Biểu tượng Thư tín (Yêu cầu xin vào nhóm)
    public static Node createMailIcon(String hexColor, double size) {
        return createIcon(SVG_MAIL, hexColor, size);
    }

    // Biểu tượng Rời nhóm
    public static Node createExitIcon(String hexColor, double size) {
        return createIcon(SVG_EXIT, hexColor, size);
    }

    // Biểu tượng Đồng hồ cát (Chờ duyệt)
    public static Node createHourglassIcon(String hexColor, double size) {
        return createIcon(SVG_HOURGLASS, hexColor, size);
    }

    // Biểu tượng Thêm người dùng
    public static Node createUserPlusIcon(String hexColor, double size) {
        return createIcon(SVG_USER_PLUS, hexColor, size);
    }

    // Biểu tượng Trục xuất / Kích người dùng
    public static Node createUserMinusIcon(String hexColor, double size) {
        return createIcon(SVG_USER_MINUS, hexColor, size);
    }
}
