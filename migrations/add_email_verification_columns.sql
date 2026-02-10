-- =====================================================
-- OTP Verification Migration - KHÔNG CẦN THIẾT
-- Description: Sử dụng field is_first_login có sẵn
-- Date: 2024-02-10
-- =====================================================

-- ℹ️ THÔNG TIN QUAN TRỌNG:
-- Database đã có sẵn column `is_first_login` (BOOLEAN DEFAULT TRUE)
-- Logic OTP verification sẽ kiểm tra field này:
--   - is_first_login = TRUE  → Yêu cầu OTP verification
--   - is_first_login = FALSE → Skip OTP (đã login lần đầu)

-- ❌ KHÔNG CẦN CHẠY MIGRATION NÀY
-- Tất cả đã có sẵn trong schema

-- 📝 Útil Commands để test/debug:

-- Reset user về first login (để test lại OTP flow):
-- UPDATE users SET is_first_login = TRUE WHERE email = 'user@example.com';
