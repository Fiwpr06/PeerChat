# LỘ TRÌNH HỌC TẬP CODEBASE PEERCHAT (BOTTOM-UP ROADMAP)

Lộ trình được sắp xếp theo đúng **Thứ tự phụ thuộc (Topological Sort)**: Từ các class nguyên thủy nhất (0 phụ thuộc) cho đến các class xử lý nghiệp vụ, mạng và giao diện người dùng (phụ thuộc nhiều class bên dưới).

---

## 🟩 TẦNG 1: CÁC COMPONENT NGUYÊN THỦY NHẤT (Độc lập 100%)
*Các hằng số, Enum, Utility độc lập và Data Model đơn giản nhất.*

1. **[ProtocolConstants.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/shared/protocol/ProtocolConstants.java)**
   * *Độ phụ thuộc: 0* — Chứa hằng số giao thức hệ thống (Cổng `5000`, `CHUNK_SIZE`, `MAX_FRAME_LENGTH`).
2. **[MessageType.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/shared/protocol/MessageType.java)**
   * *Độ phụ thuộc: 0* — Enum liệt kê các loại gói tin giao tiếp (`CONNECT_REQUEST`, `CHAT_MESSAGE`, `FILE_DATA`,...).
3. **[ChecksumUtils.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/shared/util/ChecksumUtils.java)**
   * *Độ phụ thuộc: 0* — Utility tính mã băm MD5/SHA-256 thuần của Java.
4. **[FileUtils.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/shared/util/FileUtils.java)**
   * *Độ phụ thuộc: 0* — Utility định dạng dung lượng file (KB, MB), tốc độ truyền và tạo tên file độc nhất.
5. **[ClientInfo.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/shared/model/ClientInfo.java)**
   * *Độ phụ thuộc: 0* — Model chứa thông tin Client (`clientId`, `displayName`, `ipAddress`, `port`) & JSON Parsing.

---

## 🟦 TẦNG 2: MODEL KẾT HỢP & KHUNG GÓI TIN MẠNG LOW-LEVEL
*Các class ghép nối các component từ Tầng 1.*

6. **[FileInfo.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/shared/model/FileInfo.java)**
   * *Phụ thuộc: Tầng 1 (`FileUtils`)* — Model thông tin file truyền tải (Tên, Kích thước, Checksum) & JSON Parsing.
7. **[Message.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/shared/model/Message.java)**
   * *Phụ thuộc: Tầng 1 & 2 (`FileInfo`)* — Model gói tin chứa cả tin nhắn văn bản và đối tượng file & JSON Parsing.
8. **[ProtocolMessage.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/shared/protocol/ProtocolMessage.java)**
   * *Phụ thuộc: Tầng 1 (`MessageType`, `ProtocolConstants`)* — Đóng gói/Đọc byte dữ liệu thô (`byte[]`) qua Stream TCP.

---

## 🟨 TẦNG 3: MẠNG CỐT LÕI PHÍA SERVER (Server Network Core)
*Sử dụng các Model và ProtocolMessage để xây dựng Server.*

9. **[ConnectedClient.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/server/model/ConnectedClient.java)**
   * *Phụ thuộc: `ClientInfo`, `ProtocolMessage`* — Wrapper bọc Socket và Stream của 1 Client kết nối tại Server.
10. **[ConnectionManager.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/server/network/ConnectionManager.java)**
    * *Phụ thuộc: `ConnectedClient`, `Message`, `ProtocolMessage`* — Quản lý tập trung các Client online, Broadcast dữ liệu.
11. **[FileTransferService.java (Server)](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/server/service/FileTransferService.java)**
    * *Phụ thuộc: `ConnectionManager`, `FileInfo`, `ChecksumUtils`, `FileUtils`* — Nhận chunk file và lưu trữ tại Server.
12. **[ChatService.java (Server)](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/server/service/ChatService.java)**
    * *Phụ thuộc: `ConnectionManager`, `Message`* — Xử lý logic tin nhắn chat phía Server.
13. **[ClientHandler.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/server/network/ClientHandler.java)**
    * *Phụ thuộc: Toàn bộ Tầng 1, 2, 3* — Luồng (Thread) riêng biệt xử lý các yêu cầu Socket từ từng Client.
14. **[Server.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/server/Server.java)**
    * *Phụ thuộc: `ConnectionManager`, `ClientHandler`* — ServerSocket chính lắng nghe kết nối (`accept()`).
15. **[ServerController.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/server/controller/ServerController.java)** & **[ServerApp.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/server/ServerApp.java)**
    * *Phụ thuộc: `Server`* — Giao diện quản trị Server (JavaFX) & Điểm chạy Server.

---

## 🟧 TẦNG 4: MẠNG CỐT LÕI PHÍA CLIENT (Client Network Core)
*Sử dụng ProtocolMessage và Socket để quản lý kết nối phía Client.*

16. **[ClientUtils.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/util/ClientUtils.java)**
    * *Phụ thuộc: Nguyên thủy* — Utility hiển thị & định dạng thời gian/avatar phía Client.
17. **[ClientSocket.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/network/ClientSocket.java)**
    * *Phụ thuộc: `ProtocolMessage`, `ProtocolConstants`* — Wrapper quản lý Socket kết nối tới Server.
18. **[MessageSender.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/network/MessageSender.java)** & **[MessageReceiver.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/network/MessageReceiver.java)**
    * *Phụ thuộc: `ClientSocket`, `ProtocolMessage`* — Luồng (Thread) gửi & nhận tin nhắn độc lập.

---

## 🟪 TẦNG 5: NGHIỆP VỤ & GIAO DIỆN CLIENT (Client Business & UI)
*Gồm các Service cấp cao và Giao diện JavaFX hoàn chỉnh.*

19. **[ChatService.java (Client)](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/service/ChatService.java)**
    * *Phụ thuộc: `MessageSender`, `MessageReceiver`, `Message`* — Service quản lý danh sách chat & bạn bè.
20. **[FileTransferService.java (Client)](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/service/FileTransferService.java)**
    * *Phụ thuộc: `MessageSender`, `MessageReceiver`, `FileInfo`, `ChecksumUtils`* — Service quản lý gửi/tải file.
21. **[ConnectController.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/controller/ConnectController.java)**, **[ChatController.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/controller/ChatController.java)**, **[FileTransferController.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/controller/FileTransferController.java)**
    * *Phụ thuộc: Toàn bộ Tầng 4 & 5* — Controller quản lý các màn hình JavaFX.
22. **[Main.java](file:///d:/Workspace/Practice/Java/PeerChat/src/main/java/com/peerchat/client/Main.java)**
    * *Phụ thuộc: `ConnectController`* — Điểm chạy chính (Main Entry Point) của ứng dụng Client JavaFX.
