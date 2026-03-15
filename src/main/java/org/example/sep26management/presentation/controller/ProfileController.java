package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.ChangePasswordRequest;
import org.example.sep26management.application.dto.request.UpdateProfileRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.UserProfileResponse;
import org.example.sep26management.application.service.ProfileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/v1/profile")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
@Tag(name = "User Profile", description = "Quản lý hồ sơ cá nhân: xem, cập nhật thông tin, đổi mật khẩu, upload avatar.")
public class ProfileController {

    private final ProfileService profileService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // Handle empty string → null for MultipartFile (Swagger "Send empty value")
        binder.registerCustomEditor(MultipartFile.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.trim().isEmpty()) {
                    setValue(null);
                }
            }
        });

        // Handle String → LocalDate cho multipart/form-data (format: yyyy-MM-dd)
        // @JsonFormat chỉ hoạt động với JSON body, không hoạt động với @ModelAttribute
        binder.registerCustomEditor(LocalDate.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.trim().isEmpty()) {
                    setValue(null);
                    return;
                }
                try {
                    setValue(LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE));
                } catch (DateTimeParseException e) {
                    setValue(null); // bỏ qua nếu format sai thay vì throw 500
                }
            }
        });
    }

    @GetMapping("")
    @Operation(summary = "Xem hồ sơ cá nhân", description = "Lấy thông tin profile của user đang đăng nhập.\n\n"
            + "📦 **Giải thích dữ liệu trả về (`data`):**\n"
            + "- Thông tin chi tiết: `email`, `fullName`, `phone`, `gender` (MALE/FEMALE/OTHER), `dateOfBirth`.\n"
            + "- `roleCodes`: Danh sách các quyền của hệ thống.\n"
            + "- `status`: Trạng thái (ACTIVE, INACTIVE).\n"
            + "- `avatarUrl`: Đường dẫn lấy ảnh đại diện.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        Long userId = getCurrentUserId();
        log.info(LogMessages.PROFILE_FETCHING, userId);

        ApiResponse<UserProfileResponse> response = profileService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/update-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cập nhật hồ sơ", description = "Cập nhật fullName, phone, avatar (upload file). Gửi dưới dạng `multipart/form-data`.\n\n"
            + "📦 **Giải thích dữ liệu trả về (`data`):** Trả về toàn bộ thông tin User sau khi đã Update thành công.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @ModelAttribute UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info(LogMessages.PROFILE_UPDATING, userId);

        ApiResponse<UserProfileResponse> response = profileService.updateProfile(userId, request, ipAddress,
                userAgent);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu", description = "Đổi mật khẩu tài khoản đang đăng nhập. Cần nhập `currentPassword` và `newPassword` (phải khác mật khẩu cũ, yêu cầu độ khó).\n\n"
            + "📦 **Kết quả trả về:** Message thành công, `data` rỗng.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info(LogMessages.PROFILE_CHANGING_PASSWORD, userId);

        ApiResponse<Void> response = profileService.changePassword(
                userId,
                request,
                ipAddress,
                userAgent);

        return ResponseEntity.ok(response);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        }

        Object details = authentication.getDetails();
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object userIdObj = detailsMap.get("userId");

            if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            } else if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj != null) {
                return Long.parseLong(userIdObj.toString());
            }
        }

        throw new RuntimeException(MessageConstants.USER_ID_NOT_FOUND);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}