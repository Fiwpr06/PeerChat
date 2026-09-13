package com.peerchat.server;

import com.peerchat.server.network.ClientHandler;
import com.peerchat.server.network.ConnectionManager;
import com.peerchat.server.service.ChatService;
import com.peerchat.server.service.FileTransferService;
import com.peerchat.shared.protocol.ProtocolConstants;

import java.io.IOException;
import java.net.*;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

// Lớp khởi chạy TCP Server chính của PeerChat
public class Server {
    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final int port;
    private final String connectionCode;
    private final ConnectionManager connectionManager;
    private final ChatService chatService;
    private final FileTransferService fileTransferService;
    private final ExecutorService threadPool;

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public Server(int port) {
        this.port = port;
        this.connectionCode = generateConnectionCode(ProtocolConstants.CONNECTION_CODE_LENGTH);
        this.connectionManager = new ConnectionManager();
        this.chatService = new ChatService(connectionManager);
        this.fileTransferService = new FileTransferService(connectionManager);
        this.threadPool = Executors.newCachedThreadPool();
    }

    public static void main(String[] args) {
        int port = ProtocolConstants.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Cổng không hợp lệ, dùng cổng mặc định: " + ProtocolConstants.DEFAULT_PORT);
            }
        }

        Server server = new Server(port);
        server.start();
    }

    // Khởi động ServerSocket và vòng lặp tiếp nhận Client kết nối
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;

            // Đăng ký hook tắt server an toàn khi đóng ứng dụng
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Server-Shutdown-Hook"));

            printBanner();

            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true); // Tắt Nagle để gửi gói tin tức thì

                    // Tạo luồng riêng xử lý từng client độc lập
                    ClientHandler handler = new ClientHandler(
                            clientSocket,
                            connectionCode,
                            connectionManager,
                            chatService,
                            fileTransferService
                    );

                    threadPool.submit(handler);

                } catch (SocketException e) {
                    if (!running) break;
                    LOGGER.warning("Lỗi accept socket: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("Không thể mở cổng " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    // Dừng Server và đóng toàn bộ kết nối
    public void stop() {
        if (!running) return;
        running = false;
        System.out.println("\n[SERVER] Đang dừng PeerChat Server...");
        connectionManager.closeAll();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        threadPool.shutdownNow();
        System.out.println("[SERVER] Server đã dừng hoàn toàn.");
    }

    public String getConnectionCode() { return connectionCode; }
    public int getPort() {
        if (serverSocket != null && serverSocket.isBound()) {
            return serverSocket.getLocalPort();
        }
        return port;
    }
    public boolean isRunning() { return running; }

    // In thông tin kết nối lên màn hình console Server
    private void printBanner() {
        String lanIp = getLocalLanAddress();
        System.out.println("""
            ==================================================
              PEERCHAT // TACTICAL TCP SERVER
            ==================================================
              Host Localhost : 127.0.0.1
              Host LAN IP    : %s
              Port           : %d
              Connection Code: %s
              Status         : LISTENING FOR CLIENT NODES
            ==================================================
            """.formatted(lanIp, port, connectionCode));
    }

    // Tạo mã kết nối ngẫu nhiên 5 ký tự không gây nhầm lẫn
    private static String generateConnectionCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    // Tự động tìm địa chỉ IP mạng LAN của máy chủ
    private static String getLocalLanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;

                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
