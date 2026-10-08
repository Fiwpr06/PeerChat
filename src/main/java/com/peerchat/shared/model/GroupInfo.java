package com.peerchat.shared.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

// Thông tin định danh và thành viên của một nhóm Multicast qua TCP
public class GroupInfo implements Serializable {
    private String groupId;
    private String groupName;
    private String creatorId;
    private final List<String> memberIds = new CopyOnWriteArrayList<>();
    private long createdAt;

    public GroupInfo() {
        this.createdAt = System.currentTimeMillis();
    }

    public GroupInfo(String groupId, String groupName, String creatorId) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.creatorId = creatorId;
        this.createdAt = System.currentTimeMillis();
        if (creatorId != null && !creatorId.isEmpty()) {
            this.memberIds.add(creatorId);
        }
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }

    public List<String> getMemberIds() { return Collections.unmodifiableList(memberIds); }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public void addMember(String memberId) {
        if (memberId != null && !memberId.isEmpty() && !memberIds.contains(memberId)) {
            memberIds.add(memberId);
        }
    }

    public void removeMember(String memberId) {
        if (memberId != null) {
            memberIds.remove(memberId);
        }
    }

    public boolean hasMember(String memberId) {
        return memberId != null && memberIds.contains(memberId);
    }

    public int getMemberCount() {
        return memberIds.size();
    }

    // Chuyển đối tượng GroupInfo thành chuỗi JSON
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"groupId\":\"").append(escape(groupId)).append("\",");
        sb.append("\"groupName\":\"").append(escape(groupName)).append("\",");
        sb.append("\"creatorId\":\"").append(escape(creatorId)).append("\",");
        sb.append("\"createdAt\":").append(createdAt).append(",");
        sb.append("\"memberIds\":[");
        for (int i = 0; i < memberIds.size(); i++) {
            sb.append("\"").append(escape(memberIds.get(i))).append("\"");
            if (i < memberIds.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    // Đọc đối tượng GroupInfo từ chuỗi JSON
    public static GroupInfo fromJson(String json) {
        GroupInfo info = new GroupInfo();
        info.setGroupId(extract(json, "groupId"));
        info.setGroupName(extract(json, "groupName"));
        info.setCreatorId(extract(json, "creatorId"));
        String ctStr = extract(json, "createdAt");
        if (!ctStr.isEmpty()) {
            try { info.setCreatedAt(Long.parseLong(ctStr)); } catch (NumberFormatException ignored) {}
        }

        int mbIdx = json.indexOf("\"memberIds\"");
        if (mbIdx != -1) {
            int startBracket = json.indexOf('[', mbIdx);
            int endBracket = json.indexOf(']', startBracket);
            if (startBracket != -1 && endBracket != -1) {
                String arrayContent = json.substring(startBracket + 1, endBracket);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]*)\"").matcher(arrayContent);
                while (m.find()) {
                    info.addMember(m.group(1));
                }
            }
        }
        return info;
    }

    // Chuyển danh sách GroupInfo thành mảng JSON
    public static String listToJson(List<GroupInfo> list) {
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

    // Đọc danh sách GroupInfo từ mảng JSON
    public static List<GroupInfo> listFromJson(String json) {
        List<GroupInfo> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || json.equals("[]")) return list;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[^{}]*\\}").matcher(json);
        while (m.find()) {
            list.add(GroupInfo.fromJson(m.group()));
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
        if (!(o instanceof GroupInfo that)) return false;
        return Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId);
    }

    @Override
    public String toString() {
        return "# " + groupName + " (" + getMemberCount() + " thành viên)";
    }
}
