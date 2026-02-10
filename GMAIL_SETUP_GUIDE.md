# 🔐 Hướng Dẫn Lấy Gmail App Password

## 📋 Tổng Quan

Để gửi email OTP từ ứng dụng, bạn cần tạo **App Password** từ Google. Đây là mật khẩu đặc biệt dành riêng cho ứng dụng, an toàn hơn việc dùng mật khẩu Gmail thật.

> ⚠️ **Lưu ý**: Không bao giờ dùng mật khẩu Gmail thật trong code!

---

## 🚀 Các Bước Thực Hiện

### Bước 1: Bật 2-Step Verification (Xác Thực 2 Bước)

App Password chỉ khả dụng khi bạn đã bật 2-Step Verification.

1. Truy cập: [https://myaccount.google.com/security](https://myaccount.google.com/security)

2. Tìm mục **"2-Step Verification"** (Xác thực 2 bước)

3. Click vào **"Get started"** hoặc **"Turn on"**

4. Làm theo hướng dẫn:
   - Nhập số điện thoại
   - Nhận mã xác thực qua SMS
   - Xác nhận

5. ✅ Sau khi hoàn tất, bạn sẽ thấy trạng thái **"2-Step Verification is on"**

---

### Bước 2: Tạo App Password

1. Truy cập: [https://myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)

   Hoặc:
   - Vào [https://myaccount.google.com/security](https://myaccount.google.com/security)
   - Scroll xuống mục **"How you sign in to Google"**
   - Click **"App passwords"**

2. Đăng nhập lại nếu được yêu cầu

3. Tạo App Password:
   - **Select app**: Chọn "Mail"
   - **Select device**: Chọn "Other (Custom name)"
   - Nhập tên: `SEP26 Warehouse System`

4. Click **"Generate"**

5. Google sẽ hiển thị mã 16 ký tự, ví dụ:
   ```
   abcd efgh ijkl mnop
   ```

6. ✅ **Copy mã này ngay** (bạn sẽ không thấy lại lần nữa!)

---

### Bước 3: Cấu Hình Trong Dự Án

#### A. Tạo file `.env` (nếu chưa có)

```bash
# Copy từ .env.example
cp .env.example .env
```

#### B. Cập nhật file `.env`

Mở file `.env` và điền thông tin:

```properties
# Gmail SMTP Configuration (for OTP emails)
GMAIL_USERNAME=your-email@gmail.com
GMAIL_APP_PASSWORD=abcdefghijklmnop
```

**Lưu ý**:
- ✅ **Bỏ tất cả khoảng trắng** trong App Password
  - ❌ SAI: `abcd efgh ijkl mnop`
  - ✅ ĐÚNG: `abcdefghijklmnop`
- ✅ Thay `your-email@gmail.com` bằng email Gmail của bạn
- ✅ File `.env` đã được thêm vào `.gitignore` → an toàn, không bị commit lên Git

---

### Bước 4: Kiểm Tra Kết Nối

#### Khởi động ứng dụng:

```bash
mvn spring-boot:run
```

#### Test gửi OTP:

```bash
# 1. Đăng nhập với email chưa verify
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "password123"
}

# 2. Nếu cấu hình đúng, bạn sẽ nhận được email chứa OTP
```

#### Kiểm tra logs:

```
2024-02-10 19:40:00 [main] INFO  o.e.s.a.s.OtpService - OTP sent to email: test@example.com
2024-02-10 19:40:01 [main] INFO  o.e.s.a.s.EmailService - OTP email sent successfully to: test@example.com
```

---

## 🔧 Troubleshooting (Xử Lý Lỗi)

### ❌ Lỗi: "Username and Password not accepted"

**Nguyên nhân**:
- Chưa bật 2-Step Verification
- App Password sai
- Có khoảng trắng trong App Password

**Giải pháp**:
1. Kiểm tra 2-Step Verification đã bật chưa
2. Tạo lại App Password mới
3. Bỏ tất cả khoảng trắng trong `.env`

### ❌ Lỗi: "Application-specific password required"

**Nguyên nhân**: Đang dùng mật khẩu Gmail thật thay vì App Password

**Giải pháp**: Tạo App Password theo Bước 2

### ❌ Lỗi: "Could not connect to SMTP host"

**Nguyên nhân**:
- Không có internet
- Firewall chặn port 587
- Gmail SMTP bị chặn ở quốc gia bạn

**Giải pháp**:
1. Kiểm tra kết nối internet
2. Thử port 465 (SSL) thay vì 587 (TLS)
3. Sử dụng VPN nếu cần

---

## 🔒 Bảo Mật

### ✅ Làm gì:
- ✅ Dùng App Password, không dùng mật khẩu Gmail thật
- ✅ Thêm `.env` vào `.gitignore`
- ✅ Không commit credentials lên Git
- ✅ Sử dụng environment variables trong production

### ❌ Không làm gì:
- ❌ Hard-code Gmail password trong code
- ❌ Commit file `.env` lên Git
- ❌ Share App Password công khai
- ❌ Dùng mật khẩu Gmail thật

---

## 📚 Tài Liệu Tham Khảo

- [Google Account Security](https://myaccount.google.com/security)
- [App Passwords Help](https://support.google.com/accounts/answer/185833)
- [Gmail SMTP Settings](https://support.google.com/mail/answer/7126229)

---

## 🌍 Alternative: Sử Dụng Email Domain Riêng

Nếu bạn có email domain riêng (ví dụ: `noreply@sep26.com`), bạn có thể dùng SMTP provider khác:

### SendGrid (Miễn phí 100 emails/ngày)
```properties
MAIL_HOST=smtp.sendgrid.net
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=<sendgrid-api-key>
```

### AWS SES (Simple Email Service)
```properties
MAIL_HOST=email-smtp.us-east-1.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=<aws-smtp-username>
MAIL_PASSWORD=<aws-smtp-password>
```

---

**✅ Hoàn thành!** Bây giờ ứng dụng của bạn đã có thể gửi OTP qua Gmail.
