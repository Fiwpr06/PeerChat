package com.peerchat.client.network;

import com.peerchat.shared.protocol.ProtocolMessage;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Luồng nền chuyên lắng nghe các gói tin gửi từ Server về Client
public class MessageReceiver implements Runnable {

    // Giao diện callback để thông báo khi có tin nhắn mới hoặc mất kết nối
    public interface MessageListener {
        void onMessageReceived(ProtocolMessage message);
        void onConnectionLost(String reason);
    }

    private final DataInputStream in;
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private Thread workerThread;

    public MessageReceiver(DataInputStream in) {
        this.in = in;
    }

    public void addListener(MessageListener listener) { listeners.add(listener); }
    public void removeListener(MessageListener listener) { listeners.remove(listener); }

    // Khởi động luồng lắng nghe
    public synchronized void start() {
        if (running) return;
        running = true;
        workerThread = new Thread(this, "PeerChat-ReceiverThread");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // Dừng luồng lắng nghe
    public synchronized void stop() {
        running = false;
        if (workerThread != null) workerThread.interrupt();
    }

    // Vòng lặp liên tục đọc gói tin từ Server
    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                ProtocolMessage msg;
                try {
                    msg = ProtocolMessage.readFrom(in);
                } catch (EOFException | SocketException e) {
                    notifyConnectionLost("Máy chủ đã đóng kết nối");
                    break;
                }

                notifyMessageReceived(msg);
            }
        } catch (IOException e) {
            if (running) {
                notifyConnectionLost("Lỗi mạng: " + e.getMessage());
            }
        } finally {
            running = false;
        }
    }

    private void notifyMessageReceived(ProtocolMessage message) {
        for (MessageListener listener : listeners) {
            try {
                listener.onMessageReceived(message);
            } catch (Exception e) {
                System.err.println("[LỖI XỬ LÝ GÓI TIN]: " + e.getMessage());
            }
        }
    }

    private void notifyConnectionLost(String reason) {
        if (!running) return;
        running = false;
        for (MessageListener listener : listeners) {
            try {
                listener.onConnectionLost(reason);
            } catch (Exception e) {
                System.err.println("[LỖI THÔNG BÁO MẤT KẾT NỐI]: " + e.getMessage());
            }
        }
    }
}
