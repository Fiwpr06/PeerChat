package com.peerchat.shared.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

// Dữ liệu tin nhắn chat giữa các client
public class Message implements Serializable {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private String id;
    private String senderId;
    private String senderName;
    private String targetId; // "ALL" hoặc ID của client nhận trực tiếp
    private String content;
    private long timestamp;

    public Message() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public Message(String senderId, String senderName, String targetId, String content) {
        this.id = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.senderName = senderName;
        this.targetId = targetId;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    // Định dạng giờ gửi tin nhắn (HH:mm:ss)
    public String getFormattedTime() {
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return ldt.format(TIME_FORMATTER);
    }

    // Kiểm tra có phải tin nhắn riêng hay không
    public boolean isDirect() {
        return targetId != null && !targetId.equalsIgnoreCase("ALL");
    }

    // Chuyển đối tượng tin nhắn thành chuỗi JSON
    public String toJson() {
        return "{" +
                "\"id\":\"" + escapeJson(id) + "\"," +
                "\"senderId\":\"" + escapeJson(senderId) + "\"," +
                "\"senderName\":\"" + escapeJson(senderName) + "\"," +
                "\"targetId\":\"" + escapeJson(targetId) + "\"," +
                "\"content\":\"" + escapeJson(content) + "\"," +
                "\"timestamp\":" + timestamp +
                "}";
    }

    // Đọc đối tượng tin nhắn từ chuỗi JSON
    public static Message fromJson(String json) {
        Message msg = new Message();
        msg.setId(extractJsonField(json, "id"));
        msg.setSenderId(extractJsonField(json, "senderId"));
        msg.setSenderName(extractJsonField(json, "senderName"));
        msg.setTargetId(extractJsonField(json, "targetId"));
        msg.setContent(extractJsonField(json, "content"));
        String tsStr = extractJsonField(json, "timestamp");
        if (tsStr != null && !tsStr.isEmpty()) {
            try { msg.setTimestamp(Long.parseLong(tsStr)); } catch (NumberFormatException ignored) {}
        }
        return msg;
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractJsonField(String json, String field) {
        if (json == null) return "";
        String strPattern = "\"" + field + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(strPattern).matcher(json);
        if (m.find()) return unescapeJson(m.group(1));
        String numPattern = "\"" + field + "\"\\s*:\\s*([0-9]+)";
        java.util.regex.Matcher nm = java.util.regex.Pattern.compile(numPattern).matcher(json);
        if (nm.find()) return nm.group(1);
        return "";
    }

    private static String unescapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
