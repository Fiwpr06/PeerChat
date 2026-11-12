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
    private boolean isPublic;
    private final List<String> memberIds = new CopyOnWriteArrayList<>();
    private final List<String> pendingMemberIds = new CopyOnWriteArrayList<>();
    private long createdAt;

    public GroupInfo() {
        this.createdAt = System.currentTimeMillis();
        this.isPublic = false; // Mặc định là nhóm kín cần xét duyệt
    }

    public GroupInfo(String groupId, String groupName, String creatorId) {
        this(groupId, groupName, creatorId, false);
    }

    public GroupInfo(String groupId, String groupName, String creatorId, boolean isPublic) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.creatorId = creatorId;
        this.isPublic = isPublic;
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

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public List<String> getMemberIds() { return Collections.unmodifiableList(memberIds); }
    public List<String> getPendingMemberIds() { return Collections.unmodifiableList(pendingMemberIds); }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public void addMember(String memberId) {
        if (memberId != null && !memberId.isEmpty()) {
            if (!memberIds.contains(memberId)) {
                memberIds.add(memberId);
            }
            pendingMemberIds.remove(memberId);
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

    public void addJoinRequest(String userId) {
        if (userId != null && !userId.isEmpty() && !memberIds.contains(userId) && !pendingMemberIds.contains(userId)) {
            pendingMemberIds.add(userId);
        }
    }

    public void removeJoinRequest(String userId) {
        if (userId != null) {
            pendingMemberIds.remove(userId);
        }
    }

    public boolean hasJoinRequest(String userId) {
        return userId != null && pendingMemberIds.contains(userId);
    }

    public int getPendingCount() {
        return pendingMemberIds.size();
    }

    // Chuyển đối tượng GroupInfo thành chuỗi JSON
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"groupId\":\"").append(escape(groupId)).append("\",");
        sb.append("\"groupName\":\"").append(escape(groupName)).append("\",");
        sb.append("\"creatorId\":\"").append(escape(creatorId)).append("\",");
        sb.append("\"isPublic\":").append(isPublic).append(",");
        sb.append("\"createdAt\":").append(createdAt).append(",");
        sb.append("\"memberIds\":[");
        for (int i = 0; i < memberIds.size(); i++) {
            sb.append("\"").append(escape(memberIds.get(i))).append("\"");
            if (i < memberIds.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"pendingMemberIds\":[");
        for (int i = 0; i < pendingMemberIds.size(); i++) {
            sb.append("\"").append(escape(pendingMemberIds.get(i))).append("\"");
            if (i < pendingMemberIds.size() - 1) sb.append(",");
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
        info.setPublic(extractBoolean(json, "isPublic", false));
        String ctStr = extract(json, "createdAt");
        if (!ctStr.isEmpty()) {
            try { info.setCreatedAt(Long.parseLong(ctStr)); } catch (NumberFormatException ignored) {}
        }

        extractArray(json, "memberIds", info.memberIds);
        extractArray(json, "pendingMemberIds", info.pendingMemberIds);

        return info;
    }

    private static void extractArray(String json, String field, List<String> target) {
        int mbIdx = json.indexOf("\"" + field + "\"");
        if (mbIdx != -1) {
            int startBracket = json.indexOf('[', mbIdx);
            int endBracket = json.indexOf(']', startBracket);
            if (startBracket != -1 && endBracket != -1) {
                String arrayContent = json.substring(startBracket + 1, endBracket);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]*)\"").matcher(arrayContent);
                while (m.find()) {
                    String val = m.group(1);
                    if (!target.contains(val)) {
                        target.add(val);
                    }
                }
            }
        }
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

    private static boolean extractBoolean(String json, String field, boolean defaultValue) {
        if (json == null) return defaultValue;
        String boolPattern = "\"" + field + "\"\\s*:\\s*(true|false)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(boolPattern).matcher(json);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return defaultValue;
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
        return "# " + groupName + " (" + getMemberCount() + " thành viên)" + (isPublic ? " [CÔNG KHAI]" : " [KÍN]");
    }
}
