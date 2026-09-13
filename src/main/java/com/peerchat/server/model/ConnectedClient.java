package com.peerchat.server.model;

import com.peerchat.shared.model.ClientInfo;
import com.peerchat.shared.protocol.ProtocolMessage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

// Đại diện cho một Client đang kết nối tới Server
public class ConnectedClient {
    private final String clientId;
    private final String displayName;
    private final Socket socket;
    private final DataOutputStream out;
    private final ClientInfo info;
    private final long connectedTime;

    public ConnectedClient(String clientId, String displayName, Socket socket, DataOutputStream out) {
        this.clientId = Objects.requireNonNull(clientId);
        this.displayName = Objects.requireNonNull(displayName);
        this.socket = Objects.requireNonNull(socket);
        this.out = Objects.requireNonNull(out);
        this.connectedTime = System.currentTimeMillis();

        String ip = socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "127.0.0.1";
        int port = socket.getPort();
        this.info = new ClientInfo(clientId, displayName, ip, port);
    }

    public String getClientId() { return clientId; }
    public String getDisplayName() { return displayName; }
    public Socket getSocket() { return socket; }
    public ClientInfo getInfo() { return info; }
    public long getConnectedTime() { return connectedTime; }

    // Gửi gói tin cho client (đồng bộ hóa để tránh xung đột khi nhiều luồng cùng gửi)
    public synchronized void sendMessage(ProtocolMessage msg) throws IOException {
        if (!socket.isClosed()) {
            msg.writeTo(out);
        }
    }

    // Đóng socket và giải phóng tài nguyên
    public void close() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    @Override
    public String toString() {
        return displayName + " (" + clientId.substring(0, 8) + ") @ " + info.getCoordinates();
    }
}
