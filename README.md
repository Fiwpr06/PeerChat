# PEERCHAT // NIER:AUTOMATA (YORHA SYSTEM OS)

Ứng dụng Chat và Truyền File nhị phân đa luồng theo kiến trúc **Client–Server** sử dụng **Java**, **JavaFX**, **TCP Socket thuần**, và **Multithreading**.

Dự án được xây dựng phục vụ đồ án môn học **Lập trình Mạng (Network Programming)** với tiêu chí: **Một project duy nhất**, dễ mở, dễ cấu hình, dễ demo, mã nguồn mạch lạc và tối ưu hiệu năng.

---

## 1. Giới Thiệu Tổng Quan

- **Kiến trúc mạng**: Client–Server qua giao thức TCP Socket thuần (`java.net.ServerSocket`, `java.net.Socket`). Mọi luồng dữ liệu (chat và truyền file) đều đi qua máy chủ tập trung (Centralized Server), hoàn toàn không dùng kiến trúc P2P.
- **Công nghệ cốt lõi**:
  - **Java 21 (LTS)** & **JavaFX 21** (giao diện FXML + CSS).
  - **Multithreading**: `ExecutorService` (Cached Thread Pool) trên Server và các Background Worker Threads trên Client.
  - **TCP Packet Framing**: Giao thức đóng khung tự định nghĩa loại bỏ hoàn toàn hiện tượng dính gói / phân mảnh TCP.
  - **Mã hóa băm SHA-256**: Xác thực tính toàn vẹn 100% của file nhị phân sau khi truyền.
- **Phong cách giao diện**: **NieR:Automata (YoRHa System OS)** — Tông màu giấy cổ vàng ấm (`#CDC8B4`), viền xám rêu (`#8C8675`), đen than chì (`#35332B`), hiệu ứng đảo khối tương phản kinh điển khi rê chuột (Inverted Block Hover), điểm nhấn Rust Ochre (`#B85633`). Hoàn toàn dịu mắt, chống lóa và không gây mỏi mắt khi quan sát lâu.

---

## 2. Các Tính Năng Chính

### 2.1. Quản lý Kết Nối & Xác Thực Mã Phòng
- Server tự động sinh mã kết nối ngẫu nhiên 5 ký tự (ví dụ: `8KQ2X`) loại trừ các ký tự dễ nhầm lẫn (như `0`/`O`, `1`/`I`).
- Server tự động nhận diện cả địa chỉ `127.0.0.1` (Localhost) và địa chỉ IP mạng LAN (ví dụ `192.168.1.7`) để kết nối nội mạng hoặc qua Wi-Fi.
- Client chỉ được phép vào phòng chat khi nhập đúng địa chỉ IP, cổng, mã phòng và tên hiển thị (Callsign).

### 2.2. Đồng Bộ Danh Sách Người Dùng Thời Gian Thực
- Server quản lý danh sách kết nối qua `ConnectionManager`.
- Khi có một Client mới tham gia hoặc ngắt kết nối (kể cả trường hợp tắt đột ngột/mất mạng), Server tự động phát hiện, dọn dẹp tài nguyên và gửi thông điệp `CLIENT_LIST_UPDATE` cập nhật tức thì đến toàn bộ người dùng còn lại.

### 2.3. Nhắn Tin Đa Kênh (Broadcast & Direct 1-1)
- **Chat chung (Broadcast)**: Gửi tin nhắn đến toàn bộ người dùng trong phòng.
- **Chat riêng (Direct 1-1)**: Chỉ cần nhấp chọn tên một người dùng trên danh sách online bên trái; tin nhắn sẽ được Server định tuyến trực tiếp duy nhất đến người đó một cách an toàn.
- Khung chat phân biệt màu sắc thẻ tin nhắn gửi đi (YoRHa Ochre `#B85633`) và tin nhắn nhận về (Muted Slate Taupe `#5A7382`) cùng thời gian thực gửi tin.

### 2.4. Truyền File Nhị Phân & Đối Soát SHA-256
- **Chia nhỏ khối dữ liệu (Chunking)**: File được chia nhỏ thành các chunk 64 KB (`CHUNK_SIZE`), đọc và stream qua Socket bằng bộ đệm tĩnh giúp tiết kiệm bộ nhớ RAM, truyền được cả các file dung lượng lớn.
- **Xác nhận hai chiều (Handshake)**: Client gửi yêu cầu (`FILE_REQUEST`), Client nhận hiển thị hộp thoại xác nhận và tự chọn nơi lưu file (`FILE_ACCEPT` / `FILE_REJECT`).
- **Giám sát thời gian thực**: Hiển thị thanh tiến trình (% hoàn thành), tốc độ truyền tải thực tế (KB/s, MB/s) và nút hủy truyền file (`ABORT`).
- **Toàn vẹn dữ liệu**: Tính mã băm SHA-256 dạng stream ở cả hai đầu gửi và nhận. Khi hoàn tất, hệ thống tự động so khớp và hiển thị kết quả kiểm tra toàn vẹn (`KHỚP [OK]`).

---

## 3. Giao Thức Đóng Khung Gói Tin (TCP Framing Protocol)

Do TCP là một dòng byte liên tục (byte-stream) không phân tách ranh giới gói tin, hiện tượng dính gói (packet coalescing) hoặc phân mảnh (fragmentation) có thể xảy ra. PeerChat sử dụng giao thức đóng khung nhị phân chuẩn ở tầng ứng dụng:

### 3.1. Cấu Trúc Khung Nhị Phân Chung
```
+-------------------+-------------------+-----------------------------------+
|  Length (4 bytes) |   Type (4 bytes)  |      Payload (N bytes)            |
|    int (Big Endian) |    int (Big Endian) |    UTF-8 String hoặc Binary Data  |
+-------------------+-------------------+-----------------------------------+
```
- **Length**: Kích thước phần Payload tính theo byte.
- **Type**: Mã định danh loại gói tin (tương ứng với `MessageType`).
- **Payload**: Dữ liệu thực tế.

### 3.2. Danh Sách Mã Thông Điệp (MessageType)
| Mã | Enum | Mục Đích |
| :-: | :--- | :--- |
| `1` | `CONNECT_REQUEST` | Client gửi thông tin xác thực (Mã phòng + Tên hiển thị). |
| `2` | `CONNECT_RESPONSE` | Server phản hồi kết quả xác thực (Thành công/Từ chối + ID được cấp). |
| `3` | `DISCONNECT` | Client thông báo ngắt kết nối an toàn với máy chủ. |
| `4` | `CLIENT_LIST_UPDATE` | Server broadcast danh sách các client đang trực tuyến. |
| `10` | `CHAT_MESSAGE` | Client gửi tin nhắn văn bản lên Server. |
| `11` | `CHAT_BROADCAST` | Server chuyển tiếp tin nhắn đến người nhận mục tiêu (Tất cả hoặc 1-1). |
| `20` | `FILE_REQUEST` | Người gửi đề nghị truyền một file cụ thể. |
| `21` | `FILE_ACCEPT` | Người nhận đồng ý và sẵn sàng tiếp nhận file. |
| `22` | `FILE_REJECT` | Người nhận từ chối phiên truyền file. |
| `23` | `FILE_METADATA` | Thông tin chi tiết: Tổng số chunk, kích thước file, SHA-256 file gốc. |
| `24` | `FILE_DATA` | Dữ liệu nhị phân của từng chunk (kèm UUID file và chỉ số chunk). |
| `25` | `FILE_COMPLETE` | Thông báo đã gửi toàn bộ chunk của file. |
| `26` | `FILE_STATUS` | Báo cáo trạng thái hoàn thành hoặc đối soát mã băm. |

### 3.3. Định Dạng Chunk Nhị Phân (`FILE_DATA`)
Phần payload của gói `FILE_DATA` được đóng gói nhị phân như sau:
```
+--------------------+--------------------+--------------------+--------------------+--------------------+
| FileId Len (4B)   | FileId (UTF-8)     | ChunkIndex (4B)    | Data Len (4B)      | Chunk Bytes        |
+--------------------+--------------------+--------------------+--------------------+--------------------+
```

---

## 4. Cấu Trúc Dự Án (Project Structure)

```
PeerChat/
├── .gitignore                      # Cấu hình bỏ qua file rác (target, IDE, log)
├── pom.xml                         # Cấu hình Maven, Java 21, JavaFX 21
├── START-SERVER.bat                # Kịch bản 1-click khởi chạy Server trên Windows
├── START-CLIENT.bat                # Kịch bản 1-click khởi chạy Client trên Windows
├── README.md                       # Tài liệu hướng dẫn toàn diện
│
└── src/main/
    ├── java/
    │   ├── module-info.java        # Cấu hình Java Module System (JPMS)
    │   └── com/peerchat/
    │       ├── shared/             # Thành phần dùng chung giữa Server và Client
    │       │   ├── model/
    │       │   │   ├── ClientInfo.java      # Model thông tin người dùng online
    │       │   │   ├── FileInfo.java        # Model siêu dữ liệu file và chunking
    │       │   │   └── Message.java         # Model tin nhắn chat (broadcast/direct)
    │       │   ├── protocol/
    │       │   │   ├── MessageType.java     # Định nghĩa các mã lệnh giao thức
    │       │   │   ├── ProtocolConstants.java# Hằng số cấu hình (Port, ChunkSize, Timeout)
    │       │   │   └── ProtocolMessage.java # Xử lý đóng gói & giải mã khung TCP
    │       │   └── util/
    │       │       ├── ChecksumUtils.java   # Tính toán & so khớp mã băm SHA-256
    │       │       └── FileUtils.java       # Định dạng dung lượng và tiện ích file
    │       │
    │       ├── server/             # Module máy chủ (chạy độc lập qua console)
    │       │   ├── Server.java              # Lớp chính khởi chạy ServerSocket TCP
    │       │   ├── model/
    │       │   │   └── ConnectedClient.java # Đại diện cho một kết nối client tại server
    │       │   ├── network/
    │       │   │   ├── ClientHandler.java   # Luồng độc lập lắng nghe & xử lý từng client
    │       │   │   └── ConnectionManager.java# Quản lý danh sách client và routing socket
    │       │   └── service/
    │       │       ├── ChatService.java     # Phân loại và định tuyến tin nhắn chat
    │       │       └── FileTransferService.java # Điều phối luồng truyền file qua server
    │       │
    │       └── client/             # Module giao diện và kết nối người dùng
    │           ├── Main.java                # Điểm vào JavaFX Application
    │           ├── controller/
    │           │   ├── ConnectController.java     # Điều khiển màn hình đăng nhập
    │           │   ├── ChatController.java        # Điều khiển màn hình chat & người dùng
    │           │   └── FileTransferController.java# Điều khiển tiến trình truyền file
    │           ├── model/
    │           │   ├── ClientState.java     # Trạng thái kết nối của client
    │           │   └── TransferState.java   # Trạng thái của tiến trình truyền file
    │           ├── network/
    │           │   ├── ClientSocket.java    # Quản lý kết nối Socket TCP phía client
    │           │   ├── MessageSender.java   # Luồng gửi gói tin đồng bộ & bất đồng bộ
    │           │   └── MessageReceiver.java # Luồng nền liên tục đọc gói tin từ server
    │           ├── service/
    │           │   ├── ChatService.java     # Xử lý logic nghiệp vụ chat phía client
    │           │   └── FileTransferService.java # Xử lý đọc/ghi file, chunking & SHA-256
    │           └── util/
    │               └── ClientUtils.java     # Điều phối luồng an toàn qua JavaFX UI Thread
    │
    └── resources/
        ├── css/
        │   └── style.css           # Bảng màu giao diện Industrial Console
        └── fxml/
            ├── connect.fxml        # Giao diện màn hình đăng nhập
            ├── chat.fxml           # Giao diện màn hình chat chính
            └── file-transfer.fxml  # Giao diện khối truyền file nhúng
```

---

## 5. Hướng Dẫn Cài Đặt & Chạy Ứng Dụng

### 5.1. Yêu Cầu Môi Trường
- **Hệ điều hành**: Windows 10/11, macOS, hoặc Linux.
- **Java Development Kit (JDK)**: Phiên bản **21 trở lên** (khuyến nghị Oracle JDK 21 hoặc Eclipse Temurin 21).

### 5.2. Chạy Nhanh 1-Click Trên Windows (Khuyên Dùng Để Demo)
Dự án đã tích hợp sẵn 2 kịch bản tự động:
1. **Bước 1 — Khởi động Server**:
   - Nhấp đúp vào file `START-SERVER.bat`.
   - Cửa sổ Server Console sẽ xuất hiện, tự động in địa chỉ IP và mã kết nối 5 ký tự (ví dụ: `RQ7AB`).
2. **Bước 2 — Khởi động Client 1**:
   - Nhấp đúp vào file `START-CLIENT.bat`.
   - Nhập tên hiển thị (ví dụ: `Alice`), nhập mã phòng từ server và nhấn **KẾT NỐI HỆ THỐNG**.
3. **Bước 3 — Khởi động Client 2 (và các Client tiếp theo)**:
   - Tiếp tục nhấp đúp file `START-CLIENT.bat` một lần nữa để mở cửa sổ thứ hai.
   - Nhập tên hiển thị (ví dụ: `Bob`), nhập mã phòng và nhấn **KẾT NỐI HỆ THỐNG**.
   - Hai client giờ đây đã thấy nhau trên danh sách trực tuyến và có thể bắt đầu chat hoặc gửi file!

### 5.3. Chạy Bằng Dòng Lệnh Terminal / PowerShell

1. **Biên dịch dự án**:
   ```powershell
   .\mvnw.cmd clean compile
   ```

2. **Chạy Server**:
   ```powershell
   java -cp target\classes com.peerchat.server.Server
   # Hoặc chỉ định cổng tùy ý:
   java -cp target\classes com.peerchat.server.Server 5000
   ```

3. **Chạy Client JavaFX**:
   ```powershell
   .\mvnw.cmd javafx:run
   ```

---

## 6. Bảng Tự Đánh Giá Tiêu Chí Đồ Án (Checklist)

| STT | Yêu Cầu Kỹ Thuật | Trạng Thái | Mô Tả Hiện Thực Trong Dự Án |
| :-: | :--- | :---: | :--- |
| 1 | **Mô hình Client–Server** | Hoàn tất | Server trung tâm điều phối toàn bộ kết nối, chat và chuyển tiếp file. Không dùng P2P. |
| 2 | **TCP Socket thuần** | Hoàn tất | Dùng `ServerSocket` và `Socket` thuần túy, không dùng framework phức tạp hay thư viện ngoài. |
| 3 | **Giao thức đóng khung TCP** | Hoàn tất | Định dạng chuẩn `[Length][Type][Payload]` chống dính/phân mảnh gói tin TCP tuyệt đối. |
| 4 | **Xác thực phòng chat** | Hoàn tất | Server sinh mã ngẫu nhiên 5 ký tự; kiểm tra và chỉ cho phép client hợp lệ tham gia. |
| 5 | **Đồng bộ danh sách trực tuyến** | Hoàn tất | Cập nhật tự động thời gian thực khi có client kết nối hoặc ngắt kết nối. |
| 6 | **Chat Broadcast & Direct 1-1** | Hoàn tất | Hỗ trợ chat chung cho toàn phòng và chat riêng tư 1-1 khi bấm chọn người nhận. |
| 7 | **Truyền file nhị phân theo chunk** | Hoàn tất | Chia nhỏ file thành các khối 64KB, stream trực tiếp qua Server, an toàn cho RAM. |
| 8 | **Xác nhận nhận file** | Hoàn tất | Phía nhận hiển thị hộp thoại xác nhận (Đồng ý/Từ chối) và tự chọn nơi lưu trên máy tính. |
| 9 | **Kiểm tra mã băm SHA-256** | Hoàn tất | Tự động tính và đối soát mã băm SHA-256 sau khi nhận xong, xác thực tính toàn vẹn 100%. |
| 10 | **Đa luồng (Multithreading)** | Hoàn tất | Server dùng `ExecutorService` cached thread pool; Client có luồng gửi/nhận riêng biệt. |
| 11 | **Thread Safety & JavaFX UI** | Hoàn tất | Mọi cập nhật giao diện đều qua `Platform.runLater()`; Socket stream được bảo vệ an toàn luồng. |
| 12 | **Đơn giản & Dễ demo** | Hoàn tất | Một project duy nhất, có script chạy 1-click, comment tiếng Việt ngắn gọn, dễ hiểu. |
