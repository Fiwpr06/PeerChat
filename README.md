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

### 2.1. Quản lý Kết Nối, Xác Thực Mã Phòng & Giao Diện Máy Chủ (Server GUI)
- **Giao diện điều hành Server đồ họa (Server Command Center)**:
  - Server được trang bị giao diện đồ họa trực quan chuẩn phong cách **NieR:Automata**.
  - **Khối Mã Phòng Nổi Bật (Connection Code Box)**: Hiển thị mã kết nối 5 ký tự to rõ kèm **Nút bấm 1 chạm `[ 📋 SAO CHÉP MÃ ]`** (Copy to Clipboard) giúp sao chép mã phòng tức thì để gửi cho người khác hoặc dán vào Client.
  - Tự động hiển thị và cung cấp nút copy địa chỉ IP Localhost (`127.0.0.1`) và IP mạng LAN (`192.168.x.x`).
  - **Nút Mở Nhanh Kho Lưu Trữ `[ 📂 MỞ KHO TẬP TIN ]`**: Cho phép mở trực tiếp thư mục lưu trữ tập tin `server_storage/` ngay từ chân trang giao diện máy chủ.
  - Bảng quản lý các Client đang kết nối thời gian thực và khung Console Log giám sát toàn bộ hoạt động.
  - Vẫn hỗ trợ chạy chế độ dòng lệnh thuần (CLI) khi thêm cờ `--cli` / `--nogui`.
- **Bảo mật & Trải nghiệm kết nối chuẩn mực**: Client chỉ được phép vào phòng chat khi nhập đúng địa chỉ IP, cổng, mã phòng và tên hiển thị (Callsign). Tất cả mã lỗi (sai mã phòng, mất mạng, cổng sai) được chuyển ngữ thân thiện và hiển thị sắc nét; hỗ trợ ngắt kết nối an toàn có xác nhận và tự động quay về màn hình đăng nhập.

### 2.2. Đồng Bộ Danh Sách Người Dùng Thời Gian Thực
- Server quản lý danh sách kết nối qua `ConnectionManager`.
- Khi có một Client mới tham gia hoặc ngắt kết nối (kể cả trường hợp tắt đột ngột/mất mạng), Server tự động phát hiện, dọn dẹp tài nguyên và gửi thông điệp `CLIENT_LIST_UPDATE` cập nhật tức thì đến toàn bộ người dùng còn lại.

### 2.3. Nhắn Tin Đa Kênh, Phân Lập Trò Chuyện & Bố Cục NieR Chuẩn UX
- **Kênh Chung & Chat Riêng Phân Lập Độc Lập**:
  - Dòng cố định đầu danh sách: `● KÊNH CHUNG`. Nhấp vào ai trên danh sách để chat riêng 1-1 với người đó; nhấp lại dòng đầu để quay về kênh chung toàn phòng cực kỳ trực quan mà không cần nút bấm phụ.
  - **Phân lập hội thoại nghiêm ngặt**: Tin nhắn và tập tin gửi trong Kênh Chung sẽ được toàn bộ mọi người trong phòng nhìn thấy và phản hồi (tương tự Group Chat). Khi chọn một người dùng cụ thể, luồng chat chuyển sang hội thoại riêng tư và chỉ 2 người thấy tin nhắn/tập tin của nhau.
- **Thanh Navigation (HUD Bar) Sáng Ấm & Dịu Mắt**:
  - Thanh tiêu đề trên cùng được thiết kế đồng bộ với bảng màu cát ấm (`#C8C3B0` viền `#4B473B`), loại bỏ dải đen u tối trước đây, tạo cảm giác liền mạch, sáng sủa và sắc nét.
- **Tối Ưu Bố Cục & Hỗ Trợ Co Giãn Tự Do (SplitPane)**:
  - Thanh bên trái (Danh sách trực tuyến) được rút gọn còn một nửa (~13%).
  - Thanh bên phải (Kho tài liệu) được thu gọn 30% (~20%).
  - Khung chat trung tâm được mở rộng lên **67%** tạo không gian rộng rãi để đọc tin nhắn và xem ảnh.
  - Cả 3 vùng đều nằm trong `SplitPane`, người dùng có thể dùng chuột kéo thả vách ngăn để tự do điều chỉnh chiều rộng theo nhu cầu.
- **Nút Gửi File Tiện Lợi & Phím Tắt Soạn Thảo**:
  - Nút bấm **`[ 📎 GỬI FILE ]`** được mở rộng (`125px`) đặt ngay cạnh ô nhập tin nhắn, hiển thị trọn vẹn văn bản trên mọi độ phân giải.
  - Nhấn `Enter`: Gửi tin nhắn ngay lập tức.
  - Nhấn `Shift + Enter`: Xuống dòng để soạn thảo văn bản dài nhiều đoạn (`TextArea`).
  - Hỗ trợ **kéo thả tệp tin trực tiếp (Drag & Drop)** từ máy tính vào khung chat hoặc ô nhập tin nhắn.
- **Xem Trước Hình Ảnh Trực Tiếp Trong Chat (Inline Image Preview)**:
  - Các định dạng hình ảnh (`.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp`, `.webp`) được hiển thị ảnh xem trước trực tiếp ngay bên trong bong bóng chat kèm nút **`[ ⬇ TẢI VỀ ]`** / **`[ 📂 MỞ THƯ MỤC ]`**.
  - Tệp tin thông thường hiển thị thẻ đính kèm tối giản với biểu tượng `🗎`, dung lượng, mã băm SHA-256 đối soát và thanh tiến trình tải về.
- **Phân biệt gửi / nhận trực quan**:
  - Tin nhắn của mình: Căn lề **PHẢI**, viền gạch nung YoRHa Ochre (`#B8522E`), tên "BẠN [Callsign]".
  - Tin nhắn của người khác: Căn lề **TRÁI**, kèm **Avatar huy hiệu chữ cái đầu** (`[A]`, `[B]`) màu xám than NieR (`#35332B`), tên người gửi màu xanh đá phiến (`#2B4A62`).
- **Đồng bộ lịch sử tin nhắn (Late Joiner Sync)**: Người tham gia phòng sau vẫn nhìn thấy toàn bộ tin nhắn và các tập tin được chia sẻ trước đó trong Kênh Chung nhờ bộ đệm vòng In-Memory (100 tin gần nhất) lưu trên RAM của Server (hoàn toàn không cần Database).

### 2.4. Lưu Trữ & Truyền File Store-and-Forward (Kho Tài Liệu Phòng)
- **Đính kèm & Kéo thả tập tin linh hoạt**: Người dùng có thể nhấn nút `[ 📎 GỬI FILE ]` hoặc kéo thả trực tiếp file từ máy tính vào khung chat để gửi file vào cuộc trò chuyện đang chọn (Kênh Chung hoặc Chat Riêng).
- **Cơ chế Báo Tệp Đã Mất Khi Không Tồn Tại**:
  - Nếu tệp tin bị xóa hoặc không còn trên máy chủ, khi người dùng bấm tải, hệ thống lập tức phản hồi thông báo và đổi nút thành **`❌ FILE ĐÃ MẤT`** (vô hiệu hóa nút), hoàn toàn không bị treo ở trạng thái đang tải.
- **Kho Tài Liệu Phòng (Shared Repository Panel)**:
  - Bảng bên phải liệt kê các tập tin đã được gửi trong phòng với bộ đếm số lượng file thời gian thực.
  - Hiển thị tinh gọn biểu tượng loại file (`🖼` cho ảnh, `🗎` cho tệp tin), tên file, dung lượng, người tải lên và nút bấm tải về chuẩn **`[ ⬇ TẢI VỀ ]`**.
  - Tải xong tự động chuyển sang nút **`[ 📂 MỞ THƯ MỤC ]`** giúp mở ngay thư mục chứa file mà không cần popup thông báo che khuất màn hình.
  - Tích hợp nút bật/tắt **`[ 📁 KHO TÀI LIỆU ]`** trên thanh HUD để thu gọn hoặc mở rộng toàn màn hình cho khung chat.
- **Chia nhỏ khối dữ liệu (Chunking)**: File được chia nhỏ thành các chunk 64 KB (`CHUNK_SIZE`), đọc và stream qua Socket bằng bộ đệm tĩnh giúp tiết kiệm RAM, truyền mượt mà cả file dung lượng lớn.
- **Toàn vẹn dữ liệu SHA-256**: Hệ thống tự động tính và đối soát mã băm SHA-256 sau khi tải về, hiển thị kết quả xác thực xanh `[ ĐÃ TẢI XONG // SHA-256 TOÀN VẸN 100% ]`.

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
    │       ├── server/             # Module máy chủ (hỗ trợ GUI NieR và Console)
    │       │   ├── Server.java              # Lớp chính khởi chạy ServerSocket TCP & CLI fallback
    │       │   ├── ServerApp.java           # Điểm vào JavaFX GUI cho Server
    │       │   ├── controller/
    │       │   │   └── ServerController.java# Điều khiển giao diện quản trị Server (NieR style)
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
        │   └── style.css           # Bảng màu giao diện NieR:Automata Military OS
        └── fxml/
            ├── connect.fxml        # Giao diện màn hình đăng nhập
            ├── chat.fxml           # Giao diện màn hình chat chính
            ├── file-transfer.fxml  # Giao diện khối truyền file nhúng
            └── server.fxml         # Giao diện bảng điều khiển Server (NieR:Automata OS)
```

---

## 5. Hướng Dẫn Cài Đặt & Chạy Ứng Dụng

### 5.1. Yêu Cầu Môi Trường
- **Hệ điều hành**: Windows 10/11, macOS, hoặc Linux.
- **Java Development Kit (JDK)**: Phiên bản **21 trở lên** (khuyến nghị Oracle JDK 21 hoặc Eclipse Temurin 21).

### 5.2. Chạy Nhanh 1-Click Trên Windows (Khuyên Dùng Để Demo)
Dự án đã tích hợp sẵn các kịch bản tự động tối ưu hóa, **ẩn hoàn toàn cửa sổ dòng lệnh đen (Command Prompt / Terminal)**:
1. **Bước 1 — Khởi động Server**:
   - Nhấp đúp vào file `START-SERVER.vbs` (hoặc `START-SERVER.bat`).
   - Cửa sổ điều khiển Server giao diện NieR:Automata sẽ xuất hiện trực tiếp mà không hiển thị cửa sổ terminal màu đen, tự động khởi chạy socket, hiển thị địa chỉ IP LAN và **Mã kết nối 5 ký tự** cỡ lớn.
   - Bạn chỉ cần bấm nút `[ 📋 SAO CHÉP MÃ ]` hoặc `[ 📋 SAO CHÉP IP ]` để chia sẻ cho các máy client chỉ bằng 1 cú nhấp chuột.
2. **Bước 2 — Khởi động Client 1**:
   - Nhấp đúp vào file `START-CLIENT.vbs` (hoặc `START-CLIENT.bat`).
   - Nhập tên hiển thị (ví dụ: `Alice`), dán mã phòng hoặc IP từ server và nhấn **KẾT NỐI HỆ THỐNG**.
3. **Bước 3 — Khởi động Client 2 (và các Client tiếp theo)**:
   - Tiếp tục nhấp đúp file `START-CLIENT.vbs` (hoặc `START-CLIENT.bat`) một lần nữa để mở cửa sổ thứ hai.
   - Nhập tên hiển thị (ví dụ: `Bob`), dán mã phòng và nhấn **KẾT NỐI HỆ THỐNG**.
   - Hai client giờ đây đã thấy nhau trên danh sách trực tuyến và có thể bắt đầu chat hoặc gửi file!

> [!TIP]
> **Khởi chạy không hiện Terminal**: Khuyên dùng trực tiếp file `START-SERVER.vbs` và `START-CLIENT.vbs` để chạy ứng dụng giao diện mượt mà 100% như các phần mềm desktop chuyên nghiệp (hoàn toàn không bật cửa sổ dòng lệnh). Cả 2 file `.bat` cũng đã được tối ưu để tự động ẩn terminal khi bấm chạy.

### 5.3. Chạy Bằng Dòng Lệnh Terminal / PowerShell

1. **Biên dịch dự án**:
   ```powershell
   .\mvnw.cmd clean compile
   ```

2. **Chạy Server**:
   - **Chế độ GUI (khuyến nghị)**:
     ```powershell
     java --module-path "%JFX%" --add-modules javafx.controls,javafx.fxml -cp target\classes com.peerchat.server.ServerApp
     ```
   - **Chế độ Console/CLI thuần (không cần GUI)**:
     ```powershell
     java -cp target\classes com.peerchat.server.Server --cli
     # Hoặc chỉ định cổng tùy ý:
     java -cp target\classes com.peerchat.server.Server 5000 --cli
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
| 6 | **Chat Broadcast & Direct 1-1** | Hoàn tất | Hỗ trợ chat chung cho toàn phòng và chat riêng tư 1-1 khi bấm chọn người nhận; bong bóng chat phân biệt rõ người gửi. |
| 7 | **Đồng bộ lịch sử tin nhắn** | Hoàn tất | Người vào sau (Late Joiner) nhận lại 100 tin nhắn và file cũ qua bộ đệm RAM Server mà không cần Database. |
| 8 | **Lưu trữ & Truyền file Store-and-Forward** | Hoàn tất | File được lưu tạm trên `server_storage/`, hiển thị trong Kho Tài Liệu Phòng kèm nút Tải về, không dùng popup phiền phức. |
| 9 | **Kiểm tra mã băm SHA-256** | Hoàn tất | Tự động tính và đối soát mã băm SHA-256 sau khi tải xong, xác thực tính toàn vẹn 100%. |
| 10 | **Đa luồng (Multithreading)** | Hoàn tất | Server dùng `ExecutorService` cached thread pool; Client có luồng gửi/nhận riêng biệt. |
| 11 | **Thread Safety & JavaFX UI** | Hoàn tất | Mọi cập nhật giao diện đều qua `Platform.runLater()`; Socket stream được bảo vệ an toàn luồng. |
| 12 | **Đơn giản & Dễ demo** | Hoàn tất | Một project duy nhất, có script chạy 1-click, log tiếng Anh chống lỗi font console, comment tiếng Việt dễ hiểu. |
