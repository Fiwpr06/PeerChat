package com.peerchat.shared.model;

import java.io.Serializable;
import java.util.UUID;

// Thông tin chi tiết của file truyền tải qua mạng
public class FileInfo implements Serializable {
    private String fileId;
    private String fileName;
    private long fileSize;
    private String checksum; // Mã băm SHA-256 dạng hex
    private String senderId;
    private String senderName;
    private String targetId;
    private int totalChunks; // Tổng số chunk chia nhỏ
    private int chunkSize;   // Kích thước mỗi chunk (64KB)

    public FileInfo() {
        this.fileId = UUID.randomUUID().toString();
    }

    public FileInfo(String fileName, long fileSize, String checksum, String senderId, String senderName, String targetId, int totalChunks, int chunkSize) {
        this.fileId = UUID.randomUUID().toString();
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.senderId = senderId;
        this.senderName = senderName;
        this.targetId = targetId;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    // Chuyển đối tượng FileInfo thành chuỗi JSON
    public String toJson() {
        return "{" +
                "\"fileId\":\"" + escape(fileId) + "\"," +
                "\"fileName\":\"" + escape(fileName) + "\"," +
                "\"fileSize\":" + fileSize + "," +
                "\"checksum\":\"" + escape(checksum) + "\"," +
                "\"senderId\":\"" + escape(senderId) + "\"," +
                "\"senderName\":\"" + escape(senderName) + "\"," +
                "\"targetId\":\"" + escape(targetId) + "\"," +
                "\"totalChunks\":" + totalChunks + "," +
                "\"chunkSize\":" + chunkSize +
                "}";
    }

    // Đọc đối tượng FileInfo từ chuỗi JSON
    public static FileInfo fromJson(String json) {
        FileInfo info = new FileInfo();
        info.setFileId(extract(json, "fileId"));
        info.setFileName(extract(json, "fileName"));
        String fs = extract(json, "fileSize");
        if (!fs.isEmpty()) {
            try { info.setFileSize(Long.parseLong(fs)); } catch (NumberFormatException ignored) {}
        }
        info.setChecksum(extract(json, "checksum"));
        info.setSenderId(extract(json, "senderId"));
        info.setSenderName(extract(json, "senderName"));
        info.setTargetId(extract(json, "targetId"));
        String tc = extract(json, "totalChunks");
        if (!tc.isEmpty()) {
            try { info.setTotalChunks(Integer.parseInt(tc)); } catch (NumberFormatException ignored) {}
        }
        String cs = extract(json, "chunkSize");
        if (!cs.isEmpty()) {
            try { info.setChunkSize(Integer.parseInt(cs)); } catch (NumberFormatException ignored) {}
        }
        return info;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extract(String json, String field) {
        if (json == null) return "";
        String strPattern = "\"" + field + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(strPattern).matcher(json);
        if (m.find()) return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        String numPattern = "\"" + field + "\"\\s*:\\s*([0-9]+)";
        java.util.regex.Matcher nm = java.util.regex.Pattern.compile(numPattern).matcher(json);
        if (nm.find()) return nm.group(1);
        return "";
    }
}
