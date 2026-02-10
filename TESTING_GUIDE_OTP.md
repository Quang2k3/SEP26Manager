# 🧪 Testing Guide - OTP Verification (First Login)

## 📋 Prerequisites

Trước khi test, đảm bảo bạn đã:

1. ✅ **Kiểm tra database schema**:
   ```sql
   -- Verify is_first_login column exists
   SELECT column_name, data_type, column_default 
   FROM information_schema.columns 
   WHERE table_name = 'users' AND column_name = 'is_first_login';
   ```

2. ✅ **Setup Redis**:
   ```bash
   # Option 1: Docker
   docker run -d -p 6379:6379 --name redis redis:latest
   
   # Option 2: Windows (download from Redis GitHub)
   redis-server
   ```

3. ✅ **Configure Gmail App Password** trong `.env`:
   ```env
   GMAIL_USERNAME=your-email@gmail.com
   GMAIL_APP_PASSWORD=your-16-char-app-password
   ```

4. ✅ **Start application**:
   ```bash
   mvn spring-boot:run
   ```

---

## 🔄 Test Workflow End-to-End

### Scenario 1: First Login → OTP Verification → Subsequent Login (No OTP)

#### Step 1: Tạo User Mới (first login = true)

```sql
-- Tạo user mới với is_first_login = TRUE (default)
INSERT INTO users (email, password_hash, full_name, status)
VALUES (
    'testuser@example.com',
    '$2a$10$...',  -- Use BCrypt hash for 'password123'
    'Test User',
    'ACTIVE'
);

-- Verify is_first_login = TRUE
SELECT email, is_first_login FROM users WHERE email = 'testuser@example.com';
-- Expected: is_first_login = TRUE
```

#### Step 2: Login (sẽ trigger OTP send)

**Request**:
```http
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "testuser@example.com",
  "password": "password123",
  "rememberMe": false
}
```

**Expected Response**:
```json
{
  "status": "success",
  "message": "Request processed successfully",
  "data": {
    "requiresVerification": true,
    "user": {
      "userId": 1,
      "email": "testuser@example.com",
      "fullName": "Test User"
    }
  },
  "timestamp": "2024-02-10T19:45:00"
}
```

**Check Logs**:
```
2024-02-10 19:45:00 [main] INFO  o.e.s.a.s.OtpService - Generating OTP for email: testuser@example.com
2024-02-10 19:45:01 [main] INFO  o.e.s.a.s.EmailService - Email verification OTP sent successfully to: testuser@example.com
```

**Check Email**: Bạn sẽ nhận được email với OTP 6 số (ví dụ: `123456`)

#### Step 3: Verify OTP

**Request**:
```http
POST http://localhost:8080/api/v1/auth/verify-otp
Content-Type: application/json

{
  "email": "testuser@example.com",
  "otp": "123456"
}
```

**Expected Response**:
```json
{
  "status": "success",
  "message": "Email verified successfully. You are now logged in.",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 300000,
    "requiresVerification": false,
    "user": {
      "userId": 1,
      "email": "testuser@example.com",
      "fullName": "Test User",
      "roleCodes": ["USER"],
      "avatarUrl": null
    }
  }
}
```

**Verify in Database**:
```sql
SELECT email, is_first_login, last_login_at
FROM users 
WHERE email = 'testuser@example.com';
```

Expected: `is_first_login = FALSE`, `last_login_at = NOW()`

#### Step 4: Login Again (should bypass OTP - no longer first login)

**Request**: Same as Step 2

**Expected Response**: Direct JWT token (no `requiresVerification`) vì `is_first_login = FALSE`

---

### Scenario 2: Resend OTP (after cooldown)

#### Step 1: Request OTP immediately after first send (should fail)

**Request**:
```http
POST http://localhost:8080/api/v1/auth/resend-otp
Content-Type: application/json

{
  "email": "testuser@example.com"
}
```

**Expected Response** (within 2 minutes):
```json
{
  "status": "error",
  "message": "Please wait 120 seconds before requesting a new OTP",
  "data": null
}
```

#### Step 2: Wait 2 minutes, then resend

**Check Cooldown Status in Redis**:
```bash
redis-cli
127.0.0.1:6379> TTL otp:cooldown:testuser@example.com
(integer) 45  # Remaining seconds
```

After 2 minutes:
```http
POST http://localhost:8080/api/v1/auth/resend-otp
Content-Type: application/json

{
  "email": "testuser@example.com"
}
```

**Expected Response**:
```json
{
  "status": "success",
  "message": "A new OTP has been sent to your email. Please check your inbox."
}
```

---

### Scenario 3: Invalid OTP (Brute Force Protection)

#### Step 1: Enter wrong OTP 3 times

**Attempt 1-3**:
```http
POST http://localhost:8080/api/v1/auth/verify-otp
Content-Type: application/json

{
  "email": "testuser@example.com",
  "otp": "999999"  # Wrong OTP
}
```

**Response after 3 attempts**:
```json
{
  "status": "error",
  "message": "Too many failed attempts. Please try again in 15 minutes"
}
```

**Check Redis**:
```bash
redis-cli
127.0.0.1:6379> GET otp:attempts:testuser@example.com
"3"
127.0.0.1:6379> TTL otp:attempts:testuser@example.com
(integer) 900  # 15 minutes in seconds
```

---

### Scenario 4: OTP Expiration

#### Step 1: Get OTP but don't use it for 5 minutes

**Request**: Login → Get OTP

**Wait 5 minutes**

**Verify OTP**:
```http
POST http://localhost:8080/api/v1/auth/verify-otp
Content-Type: application/json

{
  "email": "testuser@example.com",
  "otp": "123456"  # Expired OTP
}
```

**Expected Response**:
```json
{
  "status": "error",
  "message": "OTP expired or not found. Please request a new OTP"
}
```

---

## 📊 Redis Data Inspection

### Check OTP Storage

```bash
redis-cli
127.0.0.1:6379> KEYS otp:*
1) "otp:testuser@example.com"
2) "otp:cooldown:testuser@example.com"
3) "otp:attempts:testuser@example.com"

127.0.0.1:6379> GET otp:testuser@example.com
"123456"

127.0.0.1:6379> TTL otp:testuser@example.com
(integer) 245  # Remaining seconds (out of 300)
```

### Check Cooldown

```bash
127.0.0.1:6379> TTL otp:cooldown:testuser@example.com
(integer) 90  # Remaining seconds (out of 120)
```

### Check Failed Attempts

```bash
127.0.0.1:6379> GET otp:attempts:testuser@example.com
"2"  # Number of failed attempts
```

---

## 🧪 Automated Testing Script

### Using curl

```bash
#!/bin/bash

# Test 1: Login (trigger OTP send)
echo "=== Test 1: Login (trigger OTP) ==="
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "password": "password123",
    "rememberMe": false
  }' | jq

# Test 2: Resend OTP (should fail due to cooldown)
echo -e "\n=== Test 2: Resend OTP (should fail) ==="
curl -X POST http://localhost:8080/api/v1/auth/resend-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com"
  }' | jq

# Test 3: Verify OTP (enter OTP manually)
echo -e "\n=== Test 3: Verify OTP ==="
read -p "Enter OTP from email: " OTP
curl -X POST http://localhost:8080/api/v1/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"testuser@example.com\",
    \"otp\": \"$OTP\"
  }" | jq
```

---

## ✅ Expected Logs

```
2024-02-10 19:45:00 [http-nio-8080-exec-1] INFO  o.e.s.p.c.AuthController - Login attempt for email: testuser@example.com
2024-02-10 19:45:00 [http-nio-8080-exec-1] INFO  o.e.s.a.s.AuthService - Email verification required for user: testuser@example.com
2024-02-10 19:45:00 [http-nio-8080-exec-1] INFO  o.e.s.a.s.OtpService - Generating OTP for email: testuser@example.com
2024-02-10 19:45:00 [http-nio-8080-exec-1] DEBUG o.e.s.a.s.OtpService - Generated OTP: 123456 for email: testuser@example.com
2024-02-10 19:45:00 [http-nio-8080-exec-1] INFO  o.e.s.a.s.OtpService - OTP stored in Redis with TTL 5 minutes
2024-02-10 19:45:01 [task-1] INFO  o.e.s.a.s.EmailService - Email verification OTP sent successfully to: testuser@example.com
2024-02-10 19:45:01 [http-nio-8080-exec-1] INFO  o.e.s.a.s.OtpService - OTP sent successfully to email: testuser@example.com
```

---

## 🎯 Success Criteria

- ✅ User with `is_email_verified = FALSE` triggers OTP send on login
- ✅ OTP email is received within 5 seconds
- ✅ Valid OTP verification returns JWT token
- ✅ Invalid OTP increments failed attempts
- ✅ 3 failed attempts locks out for 15 minutes
- ✅ Resend cooldown works (2 minutes)
- ✅ OTP expires after 5 minutes
- ✅ Already verified users bypass OTP flow
- ✅ All actions are logged in audit_logs table

---

## 🐛 Troubleshooting

### Email not received?

1. **Check logs**:
   ```
   Failed to send email verification OTP to: ... - Error: ...
   OTP code for testing: 123456
   ```
   → OTP được log ra console khi email fail

2. **Check Gmail credentials**:
   ```bash
   echo $GMAIL_USERNAME
   echo $GMAIL_APP_PASSWORD
   ```

3. **Test SMTP connection**:
   ```bash
   telnet smtp.gmail.com 587
   ```

### Redis connection failed?

1. **Check Redis is running**:
   ```bash
   redis-cli ping
   # Expected: PONG
   ```

2. **Check application.yml**:
   ```yaml
   spring:
     data:
       redis:
         host: localhost
         port: 6379
   ```

### OTP verification always fails?

1. **Check OTP in Redis**:
   ```bash
   redis-cli GET otp:testuser@example.com
   ```

2. **Check OTP hasn't expired**:
   ```bash
   redis-cli TTL otp:testuser@example.com
   # Should be > 0
   ```

---

**✅ Happy Testing!**
