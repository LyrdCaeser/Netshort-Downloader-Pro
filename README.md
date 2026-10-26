# NetShort Downloader Pro

Ứng dụng Android để đăng nhập NetShort trong Android WebView, nhận diện URL media mà trang đang phát cung cấp cho phiên hiện tại, rồi tải file video trực tiếp khi máy chủ cấp quyền truy cập.

## Tính năng

- Đăng nhập trên WebView Android chuẩn. Ứng dụng không giả mạo user-agent để vượt kiểm tra OAuth, không in cookie và không sao chép cookie thô vào SharedPreferences. Phiên do Android WebView quản lý; trình tải chỉ lấy cookie phù hợp với URL media tại thời điểm gửi request.
- Tên cookie chỉ được dùng làm dấu hiệu tham khảo, không chứng minh tài khoản đã đăng nhập. Nếu NetShort dùng cookie phiên opaque, người dùng có thể xác nhận thủ công sau khi tự kiểm tra trạng thái trên trang.
- Môi trường duyệt NetShort cô lập trong app. Điều hướng trang chỉ cho phép `netshort.com` và một danh sách nhỏ host đăng nhập liên kết như Google, Apple và Facebook. Đây là WebView trong app, **không phải máy ảo Android/AVD**.
- Radar nhiều tín hiệu: HTML5 `<video>/<source>`, Fetch, XHR, MIME video, tài nguyên WebView và liên kết tải do WebView báo. Các link tìm được hiển thị để người dùng chọn; app không tự động tải link đầu tiên.
- Tải file video trực tiếp HTTPS (`mp4`, `m4v`, `webm`, `mov`) sau khi kiểm tra HTTP status và MIME. Android 10 trở lên lưu tại `Downloads/NetShort`; Android cũ hơn lưu vào thư mục Downloads riêng của ứng dụng.

## Giới hạn

Chỉ dùng với video bạn có quyền tải và cách sử dụng được NetShort cho phép. App không giải mã DRM, không ghép/tải playlist HLS (`.m3u8`), không bỏ qua đăng nhập hoặc quyền truy cập. Nếu NetShort hoặc nhà cung cấp OAuth từ chối đăng nhập nhúng, hãy dùng phương thức đăng nhập được NetShort hỗ trợ; app không giả mạo trình duyệt để lách chặn.

## Build

Yêu cầu JDK 17, Gradle 8.4, Android Gradle Plugin 8.2.0 và Android SDK 34. Tại thư mục repo, chạy:

```sh
gradle assembleDebug
```

APK debug được tạo tại `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions build APK và lưu artifact `app-debug-apk` khi có push lên `main`, pull request hoặc chạy workflow thủ công.
