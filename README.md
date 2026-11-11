# CameraReset 📸⚡

> **Ứng dụng Android chạy ngầm (UI-less / Headless) giúp chủ động giải phóng hook camera bị kẹt, phục hồi camera server và tự động truy vết, ghi log các tiến trình gây lỗi trực tiếp từ Bảng điều khiển nhanh (Quick Panel).**

---

## 🎯 Bối cảnh & Vấn đề giải quyết

Trên nhiều thiết bị Android (đặc biệt là Samsung Galaxy One UI), thỉnh thoảng camera gặp lỗi **"Không thể kết nối vào máy ảnh" (Camera Failed / Cannot connect to camera)** ở cả app chụp ảnh gốc, trình quét mã QR lẫn các ứng dụng ngân hàng:
* **Nguyên nhân:** Một số app (app ngân hàng, quét mã, app nền...) khi mở camera gặp sự cố hoặc thoát ngầm nhưng không đóng camera session đúng cách, khiến Binder token bị giữ lại trong `cameraserver` hoặc driver HAL camera (như Qualcomm CamX HAL) bị treo cứng.
* **Hiện tượng đặc biệt:** Khi thực hiện một cuộc gọi video (Messenger, Zalo), camera lại hoạt động bình thường, và sau cuộc gọi thì các app khác cũng dùng lại được camera! Đó là vì Android ưu tiên quyền camera cho cuộc gọi thoại/video ở mức cao nhất, tự động kích hoạt cơ chế **Client Eviction** để trục xuất các session cấp thấp đang chiếm giữ trái phép.
* **Giải pháp thông thường:** Phải khởi động lại máy hoặc cắm cáp vào máy tính chạy lệnh ADB.

👉 **CameraReset** ra đời để giải quyết triệt để vấn đề này ngay trên điện thoại **chỉ với 1 chạm**, không cần máy tính và không cần khởi động lại máy!

---

## 🚀 Tính năng nổi bật

* 🧩 **100% UI-less (Headless):** Không có giao diện rườm rà, không tạo icon rác trên màn hình chính hay khay ứng dụng (App Drawer).
* ⚡ **Nút bấm 1 chạm trên Quick Panel:** Đăng ký chuẩn `TileService` của Android gốc. Khi camera lỗi, chỉ cần vuốt thanh trạng thái xuống bấm nút **"Reset Camera"**.
* 🔍 **Truy vết & Bắt lỗi ứng dụng rò rỉ:** Tự động trích xuất `dumpsys media.camera` để tìm danh sách tiến trình đang giữ camera (`Active Camera Clients`) và nhật ký 20 sự kiện kết nối gần nhất.
* 🛑 **Buộc dừng (Force Stop) thủ phạm:** Nếu phát hiện app cụ thể đang chiếm giữ camera, app sẽ lập tức đóng tiến trình đó để giải phóng Binder lock.
* 🔄 **Xung phần cứng Video-Call (Hardware Eviction Pulse):** Mô phỏng chu kỳ mở/đóng camera ưu tiên cao tương tự cơ chế cuộc gọi video để buộc HAL driver reset về trạng thái sạch.
* 📝 **Lưu nhật ký trực tiếp vào bộ nhớ trong:** Ghi log chẩn đoán vào thư mục `/sdcard/CameraLogs/` để người dùng tiện theo dõi:
  * `history.txt`: Tóm tắt nhanh từng lần reset (thời gian, app bị bắt lỗi, kết quả).
  * `camera_reset_YYYYMMDD_HHmmss.log`: Toàn bộ dump chi tiết của `cameraserver`.
* 🌐 **Tương thích toàn diện:** Viết trên nền tảng Android AOSP gốc, hoạt động trên mọi máy Android (Samsung One UI, Google Pixel, Xiaomi HyperOS, Oppo ColorOS, Vivo OriginOS, v.v.).

---

## 📥 Cài đặt

1. Vào mục [Releases](https://github.com/WhyICantCreateMyName/CameraServer-reset-for-android/releases) để tải file APK phiên bản mới nhất: **`CameraReset.apk`**.
2. Cài đặt file APK vào điện thoại.
3. *(Khuyên dùng)* Cài đặt [Shizuku](https://shizuku.rikka.app/) và cấp quyền cho CameraReset trong mục **Authorized apps (Ứng dụng được ủy quyền)** để app có đầy đủ quyền đọc log `dumpsys` và `force-stop` app lỗi.

---

## 🛠️ Hướng dẫn sử dụng

### 1. Đưa nút ra Bảng điều khiển nhanh (Quick Panel)
* **Samsung One UI:** Vuốt 2 lần từ đỉnh màn hình xuống > Bấm biểu tượng cây bút (Sửa) > Chọn **Sửa** (Bảng điều khiển nhanh) > Tìm nút **Reset Camera** (icon xoay vòng camera) và kéo vào vị trí thuận tay nhất > Bấm **Xong**.
* **Các dòng máy khác (Xiaomi, Pixel, Oppo...):** Vuốt thanh thông báo xuống > Bấm biểu tượng Sửa (Edit Tiles) > Kéo ô **Reset Camera** lên danh sách thao tác nhanh.

### 2. Khi gặp lỗi Camera
1. **Không khởi động lại máy** (để giữ nguyên hiện trường tiến trình).
2. Vuốt Quick Panel xuống và **bấm nút "Reset Camera"**.
3. Máy sẽ rung nhẹ báo hiệu, app âm thầm giải phóng camera trong 1–2 giây và hiện thông báo Toast kết quả.
4. Mở ứng dụng Quản lý tệp (My Files) > vào thư mục **`Bộ nhớ trong / CameraLogs`** > mở file `history.txt` để xem app nào vừa gây ra sự cố!

---

## 💻 Biên dịch từ mã nguồn (Build from source)

Yêu cầu máy tính có cài sẵn **JDK 17** và **Android SDK** (hoặc Android Studio):

```bash
# Clone repository
git clone https://github.com/WhyICantCreateMyName/CameraServer-reset-for-android.git
cd CameraServer-reset-for-android

# Cấp quyền thực thi cho gradle wrapper (trên Linux/macOS)
chmod +x gradlew

# Build Debug APK
./gradlew assembleDebug

# File APK tạo ra tại:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Bản quyền & Đóng góp

* Dự án được phân phối dưới giấy phép mã nguồn mở **MIT License**.
* Mọi đóng góp, báo lỗi (Issues) hoặc Pull Request đều được hoan nghênh!

