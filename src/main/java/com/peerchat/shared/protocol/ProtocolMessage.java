package com.peerchat.shared.protocol;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// Định dạng và đóng gói gói tin TCP: [4 byte độ dài] [4 byte loại gói] [dữ liệu payload]
public class ProtocolMessage {
    private final MessageType type;
    private final byte[] payload;

    public ProtocolMessage(MessageType type, byte[] payload) {
        this.type = Objects.requireNonNull(type, "Loại gói tin không được null");
        this.payload = (payload != null) ? payload : new byte[0];
    }

    public MessageType getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int getPayloadLength() {
        return payload.length;
    }

    // Chuyển dữ liệu payload thành chuỗi văn bản UTF-8
    public String getPayloadAsText() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    // Tạo gói tin chứa chuỗi văn bản
    public static ProtocolMessage createText(MessageType type, String text) {
        byte[] bytes = (text != null) ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return new ProtocolMessage(type, bytes);
    }

    // Tạo gói tin chứa dữ liệu nhị phân
    public static ProtocolMessage createBinary(MessageType type, byte[] bytes) {
        return new ProtocolMessage(type, bytes);
    }

    // Đóng gói một chunk dữ liệu của file kèm ID và số thứ tự chunk
    public static ProtocolMessage createFileData(String fileId, int chunkIndex, byte[] chunkData) {
        byte[] idBytes = fileId.getBytes(StandardCharsets.UTF_8);
        int totalSize = 4 + idBytes.length + 4 + 4 + chunkData.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.putInt(idBytes.length);
        buffer.put(idBytes);
        buffer.putInt(chunkIndex);
        buffer.putInt(chunkData.length);
        buffer.put(chunkData);
        return new ProtocolMessage(MessageType.FILE_DATA, buffer.array());
    }

    // Lớp chứa dữ liệu chunk sau khi giải mã
    public record FileChunk(String fileId, int chunkIndex, byte[] data) {}

    // Giải mã gói tin FILE_DATA thành chunk dữ liệu
    public FileChunk parseFileData() {
        if (type != MessageType.FILE_DATA) {
            throw new IllegalStateException("Gói tin không phải là FILE_DATA");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int idLen = buffer.getInt();
        byte[] idBytes = new byte[idLen];
        buffer.get(idBytes);
        String fileId = new String(idBytes, StandardCharsets.UTF_8);
        int chunkIndex = buffer.getInt();
        int dataLen = buffer.getInt();
        byte[] data = new byte[dataLen];
        buffer.get(data);
        return new FileChunk(fileId, chunkIndex, data);
    }

    // Ghi gói tin ra luồng TCP (đồng bộ để tránh xung đột giữa chat và truyền file)
    public void writeTo(DataOutputStream out) throws IOException {
        synchronized (out) {
            out.writeInt(payload.length);
            out.writeInt(type.ordinal());
            if (payload.length > 0) {
                out.write(payload);
            }
            out.flush();
        }
    }

    // Đọc gói tin từ luồng TCP (đọc đủ số byte để tránh lỗi phân mảnh gói)
    public static ProtocolMessage readFrom(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > ProtocolConstants.MAX_FRAME_LENGTH) {
            throw new IOException("Độ dài gói tin không hợp lệ: " + length);
        }

        int typeOrdinal = in.readInt();
        MessageType[] types = MessageType.values();
        if (typeOrdinal < 0 || typeOrdinal >= types.length) {
            throw new IOException("Loại gói tin không hợp lệ: " + typeOrdinal);
        }
        MessageType type = types[typeOrdinal];

        byte[] payload = new byte[length];
        in.readFully(payload);

        return new ProtocolMessage(type, payload);
    }

    @Override
    public String toString() {
        return "ProtocolMessage{type=" + type + ", bytes=" + payload.length + "}";
    }
}
