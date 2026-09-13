package com.peerchat.shared.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Thông tin định danh của một Client kết nối trong mạng
public class ClientInfo implements Serializable {
    private String clientId;
    private String displayName;
    private String ipAddress;
    private int port;
    private long connectedTime;

    public ClientInfo() {
        this.connectedTime = System.currentTimeMillis();
    }

    public ClientInfo(String clientId, String displayName, String ipAddress, int port) {
        this.clientId = clientId;
        this.displayName = displayName;
        this.ipAddress = ipAddress;
        this.port = port;
        this.connectedTime = System.currentTimeMillis();
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public long getConnectedTime() { return connectedTime; }
    public void setConnectedTime(long connectedTime) { this.connectedTime = connectedTime; }

    // Chuỗi tọa độ mạng: IP:Port
    public String getCoordinates() {
        return (ipAddress != null ? ipAddress : "0.0.0.0") + ":" + port;
    }

    // Chuyển đối tượng ClientInfo thành chuỗi JSON
    public String toJson() {
        return "{" +
                "\"clientId\":\"" + escape(clientId) + "\"," +
                "\"displayName\":\"" + escape(displayName) + "\"," +
                "\"ipAddress\":\"" + escape(ipAddress) + "\"," +
                "\"port\":" + port + "," +
                "\"connectedTime\":" + connectedTime +
                "}";
    }

    // Đọc đối tượng ClientInfo từ chuỗi JSON
    public static ClientInfo fromJson(String json) {
        ClientInfo info = new ClientInfo();
        info.setClientId(extract(json, "clientId"));
        info.setDisplayName(extract(json, "displayName"));
        info.setIpAddress(extract(json, "ipAddress"));
        String pStr = extract(json, "port");
        if (!pStr.isEmpty()) {
            try { info.setPort(Integer.parseInt(pStr)); } catch (NumberFormatException ignored) {}
        }
        String ctStr = extract(json, "connectedTime");
        if (!ctStr.isEmpty()) {
            try { info.setConnectedTime(Long.parseLong(ctStr)); } catch (NumberFormatException ignored) {}
        }
        return info;
    }

    // Chuyển danh sách ClientInfo thành mảng JSON
    public static String listToJson(List<ClientInfo> list) {
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

    // Đọc danh sách ClientInfo từ mảng JSON
    public static List<ClientInfo> listFromJson(String json) {
        List<ClientInfo> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || json.equals("[]")) return list;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[^{}]*\\}").matcher(json);
        while (m.find()) {
            list.add(ClientInfo.fromJson(m.group()));
        }
        return list;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientInfo that)) return false;
        return Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() { return Objects.hash(clientId); }

    @Override
    public String toString() { return displayName + " [" + getCoordinates() + "]"; }
}
