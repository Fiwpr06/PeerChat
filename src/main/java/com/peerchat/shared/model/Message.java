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
    private String messageType = "TEXT"; // "TEXT" hoặc "FILE"
    private FileInfo fileInfo; // Siêu dữ liệu nếu là tin nhắn đính kèm file

    public Message() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.messageType = "TEXT";
    }

    public Message(String senderId, String senderName, String targetId, String content) {
        this.id = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.senderName = senderName;
        this.targetId = targetId;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.messageType = "TEXT";
    }

    // Khởi tạo tin nhắn đính kèm file
    public Message(String senderId, String senderName, String targetId, FileInfo fileInfo) {
        this.id = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.senderName = senderName;
        this.targetId = targetId;
        this.fileInfo = fileInfo;
        this.messageType = "FILE";
        this.content = "[TẬP TIN] " + (fileInfo != null ? fileInfo.getFileName() : "");
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
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public FileInfo getFileInfo() { return fileInfo; }
    public void setFileInfo(FileInfo fileInfo) { this.fileInfo = fileInfo; }

    // Kiểm tra xem tin nhắn có phải đính kèm file hay không
    public boolean isFileMessage() {
        return "FILE".equalsIgnoreCase(messageType) || fileInfo != null;
    }

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
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"senderId\":\"").append(escapeJson(senderId)).append("\",");
        sb.append("\"senderName\":\"").append(escapeJson(senderName)).append("\",");
        sb.append("\"targetId\":\"").append(escapeJson(targetId)).append("\",");
        sb.append("\"content\":\"").append(escapeJson(content)).append("\",");
        sb.append("\"messageType\":\"").append(escapeJson(messageType)).append("\",");
        sb.append("\"timestamp\":").append(timestamp);
        if (fileInfo != null) {
            sb.append(",\"fileInfo\":").append(fileInfo.toJson());
        }
        sb.append("}");
        return sb.toString();
    }

    // Đọc đối tượng tin nhắn từ chuỗi JSON
    public static Message fromJson(String json) {
        Message msg = new Message();
        msg.setId(extractJsonField(json, "id"));
        msg.setSenderId(extractJsonField(json, "senderId"));
        msg.setSenderName(extractJsonField(json, "senderName"));
        msg.setTargetId(extractJsonField(json, "targetId"));
        msg.setContent(extractJsonField(json, "content"));
        String mType = extractJsonField(json, "messageType");
        msg.setMessageType(mType.isEmpty() ? "TEXT" : mType);
        String tsStr = extractJsonField(json, "timestamp");
        if (tsStr != null && !tsStr.isEmpty()) {
            try { msg.setTimestamp(Long.parseLong(tsStr)); } catch (NumberFormatException ignored) {}
        }

        // Trích xuất fileInfo nếu có
        int fiIndex = json.indexOf("\"fileInfo\"");
        if (fiIndex != -1) {
            int openBrace = json.indexOf('{', fiIndex);
            if (openBrace != -1) {
                int depth = 0;
                for (int i = openBrace; i < json.length(); i++) {
                    if (json.charAt(i) == '{') depth++;
                    else if (json.charAt(i) == '}') {
                        depth--;
                        if (depth == 0) {
                            String fiJson = json.substring(openBrace, i + 1);
                            msg.setFileInfo(FileInfo.fromJson(fiJson));
                            msg.setMessageType("FILE");
                            break;
                        }
                    }
                }
            }
        }
        return msg;
    }

    // Chuyển danh sách Message thành mảng JSON
    public static String listToJson(java.util.List<Message> list) {
        StringBuilder sb = new StringBuilder("[");
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i).toJson());
                if (i < list.size() - 1) sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // Đọc danh sách Message từ mảng JSON
    public static java.util.List<Message> listFromJson(String json) {
        java.util.List<Message> list = new java.util.ArrayList<>();
        if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) return list;

        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    String objStr = json.substring(start, i + 1);
                    list.add(Message.fromJson(objStr));
                    start = -1;
                }
            }
        }
        return list;
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
