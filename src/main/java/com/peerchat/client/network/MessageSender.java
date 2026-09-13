package com.peerchat.client.network;

import com.peerchat.shared.protocol.ProtocolMessage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Xử lý gửi gói tin TCP từ Client (an toàn khi nhiều luồng cùng gửi)
public class MessageSender {
    private final DataOutputStream out;
    private final ExecutorService asyncPool = Executors.newSingleThreadExecutor();

    public MessageSender(DataOutputStream out) {
        this.out = Objects.requireNonNull(out, "DataOutputStream không được null");
    }

    // Gửi gói tin đồng bộ
    public void sendSync(ProtocolMessage msg) throws IOException {
        synchronized (out) {
            msg.writeTo(out);
        }
    }

    // Gửi gói tin bất đồng bộ ở luồng nền (không làm đơ giao diện)
    public void sendAsync(ProtocolMessage msg) {
        asyncPool.submit(() -> {
            try {
                sendSync(msg);
            } catch (IOException e) {
                System.err.println("[LỖI GỬI TIN]: " + e.getMessage());
            }
        });
    }

    // Dừng luồng gửi khi tắt client
    public void shutdown() {
        asyncPool.shutdownNow();
    }
}
