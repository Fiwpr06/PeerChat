package com.peerchat;

import com.peerchat.client.controller.FileTransferController;
import com.peerchat.server.Server;
import com.peerchat.shared.model.FileInfo;
import com.peerchat.shared.model.GroupInfo;
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

        void createGroup(String groupName) throws Exception {
            ProtocolMessage proto = ProtocolMessage.createText(MessageType.CREATE_GROUP, groupName);
            proto.writeTo(out);
        }

        void joinGroup(String groupId) throws Exception {
            ProtocolMessage proto = ProtocolMessage.createText(MessageType.JOIN_GROUP, groupId);
            proto.writeTo(out);
        }

        void leaveGroup(String groupId) throws Exception {
            ProtocolMessage proto = ProtocolMessage.createText(MessageType.LEAVE_GROUP, groupId);
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

        ProtocolMessage pollGroupUpdate(String expectedGroupName, long timeoutSeconds) throws InterruptedException {
            long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);
            while (System.currentTimeMillis() < deadline) {
                ProtocolMessage m = incomingQueue.poll(500, TimeUnit.MILLISECONDS);
                if (m != null && m.getType() == MessageType.GROUP_LIST_UPDATE) {
                    List<GroupInfo> groups = GroupInfo.listFromJson(m.getPayloadAsText());
                    if (expectedGroupName == null || groups.stream().anyMatch(g -> expectedGroupName.equals(g.getGroupName()))) {
                        return m;
                    }
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

    @Test
    @Order(7)
    @DisplayName("Kiem thu yeu cau tai file khong ton tai thi Server phan hoi FILE_STATUS NOT_FOUND")
    void testDownloadMissingFileReturnsNotFound() throws Exception {
        TestClient alice = new TestClient("Alice");

        try {
            alice.connect(serverPort, roomCode);

            // Alice yêu cầu tải file không có trên server
            String missingFileId = "missing-" + java.util.UUID.randomUUID();
            String reqJson = "{\"fileId\":\"" + missingFileId + "\"}";
            ProtocolMessage reqMsg = ProtocolMessage.createText(MessageType.FILE_DOWNLOAD_REQ, reqJson);
            reqMsg.writeTo(alice.out);

            // Server phải phản hồi gói tin FILE_STATUS với checksum là NOT_FOUND
            ProtocolMessage statusMsg = alice.pollMessage(MessageType.FILE_STATUS, 5);
            assertNotNull(statusMsg, "Server phai phan hoi FILE_STATUS khi file khong ton tai");

            FileInfo reportedInfo = FileInfo.fromJson(statusMsg.getPayloadAsText());
            assertEquals(missingFileId, reportedInfo.getFileId());
            assertEquals("NOT_FOUND", reportedInfo.getChecksum(), "Server phai bao NOT_FOUND");

        } finally {
            alice.disconnect();
        }
    }

    @Test
    @Order(8)
    @DisplayName("Kiem thu tao nhom Multicast va phat danh sach nhom GROUP_LIST_UPDATE cho tat ca Client")
    void testCreateGroupAndBroadcastGroupList() throws Exception {
        TestClient alice = new TestClient("Alice");
        TestClient bob = new TestClient("Bob");

        try {
            alice.connect(serverPort, roomCode);
            bob.connect(serverPort, roomCode);

            // Alice tạo nhóm "Nhom-Tac-Chien"
            alice.createGroup("Nhom-Tac-Chien");

            // Cả Alice và Bob đều phải nhận được gói tin GROUP_LIST_UPDATE chứa Nhom-Tac-Chien
            ProtocolMessage aliceGroupMsg = alice.pollGroupUpdate("Nhom-Tac-Chien", 5);
            assertNotNull(aliceGroupMsg, "Alice phai nhan duoc GROUP_LIST_UPDATE sau khi tao nhom");
            List<GroupInfo> aliceGroups = GroupInfo.listFromJson(aliceGroupMsg.getPayloadAsText());
            assertTrue(aliceGroups.stream().anyMatch(g -> "Nhom-Tac-Chien".equals(g.getGroupName())),
                    "Danh sach nhom phai co Nhom-Tac-Chien");

            ProtocolMessage bobGroupMsg = bob.pollGroupUpdate("Nhom-Tac-Chien", 5);
            assertNotNull(bobGroupMsg, "Bob phai nhan duoc GROUP_LIST_UPDATE khi Alice tao nhom");
            List<GroupInfo> bobGroups = GroupInfo.listFromJson(bobGroupMsg.getPayloadAsText());
            assertTrue(bobGroups.stream().anyMatch(g -> "Nhom-Tac-Chien".equals(g.getGroupName())),
                    "Bob phai thay nhom do Alice tao ra");

        } finally {
            alice.disconnect();
            bob.disconnect();
        }
    }

    @Test
    @Order(9)
    @DisplayName("Kiem thu tham gia nhom va truyen phat da huong Multicast chi toi thanh vien trong nhom")
    void testJoinGroupAndMulticastDissemination() throws Exception {
        TestClient alice = new TestClient("Alice");
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");

        try {
            alice.connect(serverPort, roomCode);
            bob.connect(serverPort, roomCode);
            charlie.connect(serverPort, roomCode);

            // Alice tạo nhóm
            alice.createGroup("Nhom-Bi-Mat");
            ProtocolMessage groupMsg = bob.pollGroupUpdate("Nhom-Bi-Mat", 5);
            assertNotNull(groupMsg);
            List<GroupInfo> groups = GroupInfo.listFromJson(groupMsg.getPayloadAsText());
            GroupInfo secretGroup = groups.stream()
                    .filter(g -> "Nhom-Bi-Mat".equals(g.getGroupName()))
                    .findFirst()
                    .orElseThrow();

            // Bob tham gia nhóm
            bob.joinGroup(secretGroup.getGroupId());

            // Alice nhận thông báo cập nhật thành viên nhóm
            ProtocolMessage updateMsg = alice.pollGroupUpdate(null, 5);
            assertNotNull(updateMsg);

            // Alice gửi tin nhắn tới nhóm Multicast (targetId = groupId)
            alice.sendMessage("Chi co nguoi trong nhom moi nhan duoc!", secretGroup.getGroupId());

            // Bob (thành viên nhóm) PHẢI nhận được tin nhắn
            ProtocolMessage bobChatMsg = bob.pollMessage(MessageType.CHAT_BROADCAST, 5);
            assertNotNull(bobChatMsg, "Bob la thanh vien nhom nen phai nhan duoc tin multicast");
            Message receivedMsg = Message.fromJson(bobChatMsg.getPayloadAsText());
            assertEquals("Chi co nguoi trong nhom moi nhan duoc!", receivedMsg.getContent());
            assertEquals(secretGroup.getGroupId(), receivedMsg.getTargetId());

            // Charlie (KHÔNG tham gia nhóm) TUYỆT ĐỐI KHÔNG nhận được tin nhắn này
            ProtocolMessage charlieChatMsg = charlie.pollMessage(MessageType.CHAT_BROADCAST, 2);
            assertNull(charlieChatMsg, "Charlie khong o trong nhom thi khong duoc nhan tin multicast");

        } finally {
            alice.disconnect();
            bob.disconnect();
            charlie.disconnect();
        }
    }

    @Test
    @Order(10)
    @DisplayName("Kiem thu roi nhom Multicast thi khong con nhan duoc tin truyen phat cua nhom nua")
    void testLeaveGroupAndMulticastExclusion() throws Exception {
        TestClient alice = new TestClient("Alice");
        TestClient bob = new TestClient("Bob");

        try {
            alice.connect(serverPort, roomCode);
            bob.connect(serverPort, roomCode);

            alice.createGroup("Nhom-Tam-Thoi");
            ProtocolMessage gMsg = bob.pollGroupUpdate("Nhom-Tam-Thoi", 5);
            assertNotNull(gMsg);
            List<GroupInfo> groups = GroupInfo.listFromJson(gMsg.getPayloadAsText());
            GroupInfo tempGroup = groups.stream()
                    .filter(g -> "Nhom-Tam-Thoi".equals(g.getGroupName()))
                    .findFirst()
                    .orElseThrow();

            // Bob vào nhóm
            bob.joinGroup(tempGroup.getGroupId());
            Thread.sleep(300);

            // Bob rời nhóm
            bob.leaveGroup(tempGroup.getGroupId());
            Thread.sleep(300);

            // Alice gửi tin nhắn vào nhóm
            alice.sendMessage("Bob da roi nhom chua?", tempGroup.getGroupId());

            // Bob không được nhận tin nhắn nữa
            ProtocolMessage bobMsg = bob.pollMessage(MessageType.CHAT_BROADCAST, 2);
            assertNull(bobMsg, "Bob da roi nhom thi khong duoc nhan tin multicast cua nhom nua");

        } finally {
            alice.disconnect();
            bob.disconnect();
        }
    }
}