# Advanced Android OCR & QR Restoration

Một ứng dụng Android mã nguồn mở mạnh mẽ, kết hợp sức mạnh của **OpenCV**, **Google ML Kit** và **ZXing** để làm nét, phục hồi và giải mã các loại mã QR/Barcode bị mờ, hỏng, đứt nét hoặc rách nát.

---

## 🌟 Tính năng Nổi bật

Ứng dụng cung cấp một **Pipeline Xử lý ảnh (Image Processing)** độc lập và tương tác trực tiếp theo từng bước để bạn có thể tự tay "chữa cháy" cho các mã QR không thể đọc được bằng Camera thường.

### 1. Công cụ Xử lý Ảnh Chuyên Sâu (OpenCV)
*   **Crop Rect (Cắt xén tương tác)**: Hỗ trợ vẽ mảng và vuốt chạm kéo/thả (Drag & Resize) khung cắt trực tiếp trên bề mặt ảnh để cô lập mã vạch cần quét.
*   **CLAHE (Cân bằng Sáng Tương thích)**: Khử bóng râm, tự động cân bằng những vùng sáng/tối không đều đặn trên bề mặt giấy in.
*   **QR Aligner (Dò góc & Nắn phẳng)**:
    *   **Find QR**: Quét đa tầng thuật toán để tự động truy vết 4 góc mốc của mã QR bị chụp nghiêng/chéo.
    *   **Warp QR**: Dùng ma trận biến đổi phối cảnh (Perspective Warp) để nắn và bóc tách đoạn mã QR đó về lại đúng nguyên bản kích thước gốc thành một hình vuông phẳng phiu hoàn hảo.
*   **AI-Like Digital Reconstruct (Tái tạo Digital)**: Thuật toán độc quyền mô phỏng Super Resolution. Nó có khả năng tính toán kích thước lưới ma trận bề mặt (vd: 25x25, 29x29) của một QR code bị vò nát nhăn nheo, sau đó nội suy và "vẽ lại" một bộ mã QR Digital phân giải cao, khối vuông sắc lẹm "như vừa in ra từ máy tính".
*   **Threshold (Cắt Ngưỡng Trắng Đen)**: Hỗ trợ Otsu và Gaussian Adaptive để tách bạch mực in khỏi nền giấy.
*   **Morphology (Hình thái học)**: Phép toán **Close (Đóng)** được tinh chỉnh đặc biệt để trở thành cây cầu nối liền các đứt gãy giữa ký tự/vạch mã QR bị rách xước.

### 2. Lõi Giải Mã (Decoders)
*   **Google ML Kit Vision**: Lõi Machine Learning quét Barcode/QR cực nhạy của Google.
*   **ZXing (Zebra Crossing)**: Lõi mã nguồn mở kinh điển siêu nhẹ quét đa định dạng.

---

## 🛠 Công nghệ Sử dụng (Tech Stack)

*   **Ngôn ngữ**: Kotlin
*   **Hệ điều hành**: Android 7.0+ (minSdk 24)
*   **Thư viện Thị giác Máy tính**: OpenCV 4.5.3 (QuickBirdStudios)
*   **Thư viện OCR**:
    *   `com.google.mlkit:barcode-scanning`
    *   `com.google.zxing:core`

---

## 🚀 Hướng dấn Cài đặt và Sử dụng

### Dành cho nhà phát triển (Android Studio):
1. Clone / Mở dự án bằng **Android Studio**.
2. Đợi Gradle Sync hoàn tất (dự án cài sẵn Gradle 7.5.1).
3. Ấn **Run** để cài đặt APK lên thiết bị thật hoặc máy ảo (Emulator).

### Luồng Thao tác Chữa Mã QR (Quy trình chuẩn):
1.  **Nạp ảnh**: Ấn nút `Load` để chọn ảnh chứa QR code bị vỡ/nhoè từ thư viện máy.
2.  **Cắt vùng (Tùy chọn)**: Tích vào `Crop Rect` -> Khoanh vùng mã QR -> Ấn `Do Crop`.
3.  **Khử bóng (Tùy chọn)**: Ấn `Apply CLAHE` nếu ảnh bị lóa sáng hoặc chìm vào bóng tối.
4.  **Bẻ thẳng ảnh**: 
    1. Ấn `Find QR` để hệ thống đóng khung màu xanh lá định vị mã vạch.
    2. Ấn `Warp QR` để cắt phẳng và ép thành khung vuông chuẩn.
5.  **Tái tạo khối (Quan trọng với ảnh nhăn/mờ)**: 
    * Ấn thẳng vào `AI-Like Digital Reconstruct` để đồ họa vi tính dập lại toàn bộ các khối nhiễu thành các khối vuông nét căng.
6.  **Thuật toán Vá mã (Nếu xước rách)**: Chọn `Close (Fix Breaks)` từ Menu Morph -> Ấn `Apply Morph`.
7.  **Kiểm tra Thành quả**: Chọn lõi thư viện `Google ML Kit` hoặc `ZXing` -> Bấm `Test Image`. Kết quả đường link / văn bản sẽ rớt ra ở dưới cùng màn hình!
    *(Mẹo: Bạn có thể ấn `Reset Image` để hủy toàn bộ quá trình áp dụng filter và làm lại từ bước ảnh khoanh Crop ban đầu).*

---

## 📝 Giấy phép (License)
Toàn bộ mã nguồn này được xây dựng cho mục đích học tập và chia sẻ (Open Source). Bạn có thể tự do mở rộng và tùy biến thêm.
