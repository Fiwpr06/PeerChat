package com.peerchat.client.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

// Quản lý kết nối TCP Socket từ Client tới Server
public class ClientSocket {
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    // Kết nối tới Server theo IP, Port và thời gian timeout
    public synchronized void connect(String host, int port, int timeoutMs) throws IOException {
        disconnect();

        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true); // Gửi gói tin tức thì, không gom gói

        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());
    }

    // Kiểm tra socket có đang mở và kết nối hay không
    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized DataInputStream getInputStream() { return inputStream; }
    public synchronized DataOutputStream getOutputStream() { return outputStream; }
    public synchronized Socket getSocket() { return socket; }

    // Đóng kết nối an toàn và giải phóng luồng
    public synchronized void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        socket = null;
        inputStream = null;
        outputStream = null;
    }
}
