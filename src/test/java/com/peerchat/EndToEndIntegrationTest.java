package com.peerchat;

import com.peerchat.client.controller.FileTransferController;
import com.peerchat.server.Server;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.model.Message;
import com.peerchat.shared.protocol.MessageType;
import com.peerchat.shared.protocol.ProtocolConstants;
import com.peerchat.shared.protocol.ProtocolMessage;
import com.peerchat.shared.util.ChecksumUtils;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndIntegrationTest {
    private static Server testServer;
    private static Thread serverThread;
    private static int serverPort = 58900;
    private static String roomCode;

    static class TestClient {
        final String name;
        Socket socket;
        DataInputStream in;
        DataOutputStream out;
        String clientId;
        final BlockingQueue<ProtocolMessage> incomingQueue = new LinkedBlockingQueue<>();
        volatile boolean running = true;
        Thread readerThread;

        TestClient(String name) {
            this.name = name;
        }

        void connect(int port, String code) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setTcpNoDelay(true);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            String reqJson = "{\"connectionCode\":\"" + code + "\",\"displayName\":\"" + name + "\"}";
            ProtocolMessage reqMsg = ProtocolMessage.createText(MessageType.CONNECT_REQUEST, reqJson);
            reqMsg.writeTo(out);

            ProtocolMessage resp = ProtocolMessage.readFrom(in);
            assertEquals(MessageType.CONNECT_RESPONSE, resp.getType());
            String respJson = resp.getPayloadAsText();
            assertTrue(respJson.contains("\"success\":true"), "Xac thuc phai thanh cong");

            int idx = respJson.indexOf("\"clientId\":\"");
            if (idx != -1) {
                int end = respJson.indexOf("\"", idx + 12);
                this.clientId = respJson.substring(idx + 12, end);
            }

            readerThread = new Thread(() -> {
                try {
                    while (running && !socket.isClosed()) {
                        ProtocolMessage m = ProtocolMessage.readFrom(in);
                        incomingQueue.put(m);
                    }
                } catch (Exception ignored) {}
            });
            readerThread.setDaemon(true);
            readerThread.start();
        }

        void sendMessage(String content, String targetId) throws Exception {
            Message msg = new Message(clientId, name, targetId, content);
            ProtocolMessage proto = ProtocolMessage.createText(MessageType.CHAT_MESSAGE, msg.toJson());
            proto.writeTo(out);
        }

        ProtocolMessage pollMessage(MessageType expectedType, long timeoutSeconds) throws InterruptedException {
            long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);
            while (System.currentTimeMillis() < deadline) {
                ProtocolMessage m = incomingQueue.poll(500, TimeUnit.MILLISECONDS);
                if (m != null && m.getType() == expectedType) {
                    return m;
                }
            }
            return null;
        }

        void disconnect() {
            running = false;
            try {
                if (out != null) {
                    new ProtocolMessage(MessageType.DISCONNECT, new byte[0]).writeTo(out);
                }
            } catch (Exception ignored) {}
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {}
        }
    }

    @BeforeAll
    static void startServer() throws Exception {
        testServer = new Server(serverPort);
        serverThread = new Thread(testServer::start, "Test-Server-Thread");
        serverThread.setDaemon(true);
        serverThread.start();

        int waitCount = 0;
        while (!testServer.isRunning() && waitCount < 50) {
            Thread.sleep(100);
            waitCount++;
        }
        assertTrue(testServer.isRunning(), "Server phai khoi dong thanh cong");
        roomCode = testServer.getConnectionCode();
        assertNotNull(roomCode);
        assertEquals(ProtocolConstants.CONNECTION_CODE_LENGTH, roomCode.length());
    }

    @AfterAll
    static void stopServer() {
        if (testServer != null) {
            testServer.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Kiem thu dinh dang icon va nhan dien tap tin anh")
    void testFileHelpers() {
        assertTrue(FileTransferController.isImageFile("avatar.png"));
        assertTrue(FileTransferController.isImageFile("photo.JPG"));
        assertTrue(FileTransferController.isImageFile("graphic.webp"));
        assertTrue(FileTransferController.isImageFile("scene.gif"));
        assertFalse(FileTransferController.isImageFile("document.pdf"));
        assertFalse(FileTransferController.isImageFile("archive.zip"));

        assertEquals("🖼", FileTransferController.getSimpleFileIcon("picture.png"));
        assertEquals("🗎", FileTransferController.getSimpleFileIcon("report.docx"));
        assertEquals("🗎", FileTransferController.getSimpleFileIcon("code.java"));
    }

    @Test
    @Order(2)
    @DisplayName("Kiem thu tu choi khi nhap sai ma phong")
    void testAuthenticationRejection() throws Exception {
        try (Socket s = new Socket("127.0.0.1", serverPort);
             DataOutputStream out = new DataOutputStream(s.getOutputStream());
             DataInputStream in = new DataInputStream(s.getInputStream())) {
            s.setTcpNoDelay(true);

            String reqJson = "{\"connectionCode\":\"WRONG\",\"displayName\":\"Hacker\"}";
            ProtocolMessage reqMsg = ProtocolMessage.createText(MessageType.CONNECT_REQUEST, reqJson);
            reqMsg.writeTo(out);

            ProtocolMessage resp = ProtocolMessage.readFrom(in);
            assertEquals(MessageType.CONNECT_RESPONSE, resp.getType());
            assertTrue(resp.getPayloadAsText().contains("\"success\":false"));
        }
    }

    @Test
    @Order(3)
    @DisplayName("Kiem thu ket noi, chat Kenh Chung va dong bo danh sach nguoi dung")
    void testPublicGroupChatAndPeerSync() throws Exception {
        TestClient alice = new TestClient("Alice");
        TestClient bob = new TestClient("Bob");

        try {
            alice.connect(serverPort, roomCode);
            assertNotNull(alice.clientId);

            bob.connect(serverPort, roomCode);
            assertNotNull(bob.clientId);

            ProtocolMessage updateMsg = null;
            long deadline = System.currentTimeMillis() + 5000;
            boolean hasBob = false;
            while (System.currentTimeMillis() < deadline) {
                ProtocolMessage m = alice.pollMessage(MessageType.CLIENT_LIST_UPDATE, 2);
                if (m != null && m.getPayloadAsText().contains("Bob")) {
                    hasBob = true;
                    updateMsg = m;
                    break;
                }
            }
            assertTrue(hasBob, "Alice phai nhan CLIENT_LIST_UPDATE sau khi Bob vao phong");

            alice.sendMessage("Chao toan the phong chat!", ProtocolConstants.TARGET_ALL);

            ProtocolMessage chatBroadcast = bob.pollMessage(MessageType.CHAT_BROADCAST, 5);
            assertNotNull(chatBroadcast, "Bob phai nhan duoc CHAT_BROADCAST");
            Message receivedMsg = Message.fromJson(chatBroadcast.getPayloadAsText());
            assertEquals("Chao toan the phong chat!", receivedMsg.getContent());
            assertEquals(alice.name, receivedMsg.getSenderName());
            assertEquals(ProtocolConstants.TARGET_ALL, receivedMsg.getTargetId());
            assertFalse(receivedMsg.isDirect());

        } finally {
            alice.disconnect();
            bob.disconnect();
        }
    }

    @Test
    @Order(4)
    @DisplayName("Kiem thu phan lap hoi thoai (Chat Rieng 1-1 khong bi lo ra ngoai)")
    void testPrivateDirectChatIsolation() throws Exception {
        TestClient alice = new TestClient("Alice");
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");

        try {
            alice.connect(serverPort, roomCode);
            bob.connect(serverPort, roomCode);
            charlie.connect(serverPort, roomCode);

            alice.sendMessage("Tin nhan bi mat chi danh cho Bob", bob.clientId);

            ProtocolMessage bobMsg = bob.pollMessage(MessageType.CHAT_BROADCAST, 5);
            assertNotNull(bobMsg, "Bob phai nhan duoc tin nhan rieng tu Alice");
            Message msgForBob = Message.fromJson(bobMsg.getPayloadAsText());
            assertEquals("Tin nhan bi mat chi danh cho Bob", msgForBob.getContent());
            assertTrue(msgForBob.isDirect());
            assertEquals(bob.clientId, msgForBob.getTargetId());

            ProtocolMessage charlieMsg = charlie.pollMessage(MessageType.CHAT_BROADCAST, 2);
            assertNull(charlieMsg, "Charlie khong duoc nhan tin nhan rieng giua Alice va Bob");

        } finally {
            alice.disconnect();
            bob.disconnect();
            charlie.disconnect();
        }
    }

    @Test
    @Order(5)
    @DisplayName("Kiem thu truyen tap tin nhi phan da chunk va doi soat SHA-256 toan ven 100%")
    void testFileTransferStoreAndForwardWithSha256() throws Exception {
        TestClient alice = new TestClient("Alice");
        TestClient bob = new TestClient("Bob");

        File tempSendFile = File.createTempFile("peerchat_test_send_", ".dat");
        File tempRecvFile = File.createTempFile("peerchat_test_recv_", ".dat");
        tempSendFile.deleteOnExit();
        tempRecvFile.deleteOnExit();

        try {
            byte[] fileBytes = new byte[140 * 1024];
            new SecureRandom().nextBytes(fileBytes);
            Files.write(tempSendFile.toPath(), fileBytes);

            String expectedHash = ChecksumUtils.calculateSHA256(tempSendFile);
            assertNotNull(expectedHash);

            alice.connect(serverPort, roomCode);
            bob.connect(serverPort, roomCode);

            int chunkSize = ProtocolConstants.CHUNK_SIZE;
            int totalChunks = (int) Math.ceil((double) fileBytes.length / chunkSize);

            FileInfo sendInfo = new FileInfo(
                    tempSendFile.getName(),
                    fileBytes.length,
                    expectedHash,
                    alice.clientId,
                    alice.name,
                    ProtocolConstants.TARGET_ALL,
                    totalChunks,
                    chunkSize
            );
            String fileId = sendInfo.getFileId();

            ProtocolMessage metaMsg = ProtocolMessage.createText(MessageType.FILE_METADATA, sendInfo.toJson());
            metaMsg.writeTo(alice.out);

            int offset = 0;
            for (int chunkIdx = 0; chunkIdx < totalChunks; chunkIdx++) {
                int len = Math.min(chunkSize, fileBytes.length - offset);
                byte[] chunkData = new byte[len];
                System.arraycopy(fileBytes, offset, chunkData, 0, len);

                ProtocolMessage dataMsg = ProtocolMessage.createFileData(fileId, chunkIdx, chunkData);
                dataMsg.writeTo(alice.out);
                offset += len;
            }

            ProtocolMessage completeMsg = ProtocolMessage.createText(MessageType.FILE_COMPLETE, sendInfo.toJson());
            completeMsg.writeTo(alice.out);

            ProtocolMessage fileBroadcast = bob.pollMessage(MessageType.CHAT_BROADCAST, 5);
            assertNotNull(fileBroadcast, "Bob phai nhan duoc thong bao file");
            Message receivedFileMsg = Message.fromJson(fileBroadcast.getPayloadAsText());
            assertTrue(receivedFileMsg.isFileMessage());
            assertEquals(fileId, receivedFileMsg.getFileInfo().getFileId());

            String reqDownloadJson = "{\"fileId\":\"" + fileId + "\"}";
            ProtocolMessage dlReq = ProtocolMessage.createText(MessageType.FILE_DOWNLOAD_REQ, reqDownloadJson);
            dlReq.writeTo(bob.out);

            ProtocolMessage dlMeta = bob.pollMessage(MessageType.FILE_METADATA, 5);
            assertNotNull(dlMeta, "Bob phai nhan FILE_METADATA tu Server");

            ByteArrayOutputStream downloadedBytes = new ByteArrayOutputStream();
            int receivedChunks = 0;

            while (receivedChunks < totalChunks) {
                ProtocolMessage chunkMsg = bob.pollMessage(MessageType.FILE_DATA, 5);
                assertNotNull(chunkMsg, "Bob phai nhan FILE_DATA chunk " + receivedChunks);
                ProtocolMessage.FileChunk chunk = chunkMsg.parseFileData();
                assertEquals(fileId, chunk.fileId());
                assertEquals(receivedChunks, chunk.chunkIndex());
                downloadedBytes.write(chunk.data());
                receivedChunks++;
            }

            ProtocolMessage dlComplete = bob.pollMessage(MessageType.FILE_DOWNLOAD_COMPLETE, 5);
            assertNotNull(dlComplete, "Bob phai nhan FILE_DOWNLOAD_COMPLETE");

            Files.write(tempRecvFile.toPath(), downloadedBytes.toByteArray());
            String actualHash = ChecksumUtils.calculateSHA256(tempRecvFile);

            assertEquals(expectedHash, actualHash, "SHA-256 phai khop 100%");

        } finally {
            alice.disconnect();
            bob.disconnect();
            tempSendFile.delete();
            tempRecvFile.delete();
        }
    }

    @Test
    @Order(6)
    @DisplayName("Kiem thu dong bo lich su tin nhan cho nguoi vao sau (Late Joiner Sync)")
    void testLateJoinerSync() throws Exception {
        TestClient alice = new TestClient("Alice");
        TestClient bob = new TestClient("Bob");

        try {
            alice.connect(serverPort, roomCode);
            bob.connect(serverPort, roomCode);

            alice.sendMessage("Tin nhan cong khai 1", ProtocolConstants.TARGET_ALL);
            alice.sendMessage("Tin nhan cong khai 2", ProtocolConstants.TARGET_ALL);
            alice.sendMessage("Bi mat rieng tu khong lot ra ngoai", bob.clientId);

            Thread.sleep(500);

            TestClient david = new TestClient("David");
            david.connect(serverPort, roomCode);

            ProtocolMessage histMsg = david.pollMessage(MessageType.CHAT_HISTORY, 5);
            assertNotNull(histMsg, "David phai nhan duoc goi CHAT_HISTORY");

            List<Message> history = Message.listFromJson(histMsg.getPayloadAsText());
            assertFalse(history.isEmpty(), "Lich su khong duoc rong");

            boolean foundPublic1 = history.stream().anyMatch(m -> "Tin nhan cong khai 1".equals(m.getContent()));
            boolean foundPublic2 = history.stream().anyMatch(m -> "Tin nhan cong khai 2".equals(m.getContent()));
            boolean foundSecret = history.stream().anyMatch(m -> m.getContent().contains("Bi mat"));

            assertTrue(foundPublic1, "David phai thay tin nhan cong khai 1");
            assertTrue(foundPublic2, "David phai thay tin nhan cong khai 2");
            assertFalse(foundSecret, "David tuyet doi KHONG duoc thay tin nhan rieng tu cua nguoi khac");

            david.disconnect();

        } finally {
            alice.disconnect();
            bob.disconnect();
        }
    }
}