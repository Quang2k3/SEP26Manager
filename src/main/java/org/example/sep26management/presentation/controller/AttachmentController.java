package org.example.sep26management.presentation.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import java.util.Set;

/**
 * POST /v1/attachments/upload
 * Endpoint chung để upload ảnh bằng chứng (QC FAIL photo, v.v.)
 * — Dùng Cloudinary giống SignedNoteService, không gắn với entity cụ thể.
 * — Trả về { url: "https://..." } để FE lưu vào scan request.
 */
@RestController
@RequestMapping("/v1/attachments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Attachment & Photo API", description = "Các API dùng để tải ảnh bằng chứng lên Cloudinary và đồng bộ ảnh qua QR code")
public class AttachmentController {

    private final Cloudinary cloudinary;
    private final org.example.sep26management.application.service.UploadSessionService uploadSessionService;

    private static final long   MAX_SIZE = 15L * 1024 * 1024; // 15 MB
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp", "heic", "heif");

    /**
     * TẠO SESSION UPLOAD ẢNH QUA QR CODE
     * GET /v1/attachments/session
     */
    @Operation(summary = "Tạo phiên QR Code", description = "Sinh ra một UUID tạm thời (sống 15 phút) dùng để quét mã QR bằng điện thoại.")
    @GetMapping("/session")
    public ResponseEntity<ApiResponse<Map<String, String>>> createSession() {
        String uuid = uploadSessionService.createSession();
        return ResponseEntity.ok(ApiResponse.success("Session created", Map.of("uuid", uuid)));
    }

    /**
     * HOÀN TẤT UPLOAD ẢNH TỪ ĐIỆN THOẠI
     * POST /v1/attachments/session/{uuid}
     */
    @Operation(summary = "Hoàn tất ghép nối phiên QR", description = "Cập nhật URL ảnh đã upload trên điện thoại vào UUID tương ứng để PC tải về.")
    @PostMapping("/session/{uuid}")
    public ResponseEntity<ApiResponse<Void>> completeSession(
            @Parameter(description = "UUID của mã QR") @PathVariable String uuid,
            @RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isEmpty()) {
            throw new BusinessException("Vui lòng cung cấp URL ảnh.");
        }
        uploadSessionService.completeSession(uuid, url);
        return ResponseEntity.ok(ApiResponse.success("Đã đồng bộ ảnh thành công", null));
    }

    /**
     * PC POLLING LẤY ẢNH TỪ ĐIỆN THOẠI
     * GET /v1/attachments/session/{uuid}
     */
    @Operation(summary = "Lấy URL ảnh báo cáo", description = "PC gọi (pollling) liên tục mỗi 3s để kiểm tra xem tải ảnh bằng điện thoại qua UUID này đã thành công chưa.")
    @GetMapping("/session/{uuid}")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSessionPhoto(
            @Parameter(description = "UUID của mã QR") @PathVariable String uuid) {
        String url = uploadSessionService.getSessionUrl(uuid);
        if (url == null) {
            throw new BusinessException("Session upload không tồn tại hoặc đã hết hạn.");
        }
        if (url.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("Đang chờ ảnh từ điện thoại...", Map.of()));
        }
        return ResponseEntity.ok(ApiResponse.success("Đã có ảnh", Map.of("url", url)));
    }

    /**
     * UPLOAD ẢNH TỪ ĐIỆN THOẠI LÊN CLOUDINARY (TRẢ VỀ CHO SESSION)
     * POST /v1/attachments/session/{uuid}/upload
     * Body: multipart/form-data, field name = "photo"
     */
    @Operation(summary = "Upload ảnh ẩn danh bằng điện thoại", description = "Tải ảnh từ Camera điện thoại siêu mượt bằng File Part nén vào public UUID (không yêu cầu JWT đăng nhập).")
    @PostMapping("/session/{uuid}/upload")
    public ResponseEntity<ApiResponse<Void>> uploadSessionPhoto(
            @Parameter(description = "UUID của mã QR đang quét") @PathVariable String uuid,
            @Parameter(description = "File ảnh bằng chứng (multipart/form-data)") @RequestParam("photo") MultipartFile photo) {
            
        // 1. Verify session exists
        String currentUrl = uploadSessionService.getSessionUrl(uuid);
        if (currentUrl == null) {
            throw new BusinessException("Mã QR không hợp lệ hoặc đã hết phiên.");
        }

        // 2. Upload to Cloudinary
        if (photo == null || photo.isEmpty())
            throw new BusinessException("Vui lòng chọn ảnh.");
        if (photo.getSize() > MAX_SIZE)
            throw new BusinessException("Ảnh quá lớn. Tối đa 15MB.");

        String filename = photo.getOriginalFilename() != null
                ? photo.getOriginalFilename().toLowerCase() : "";
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1) : "";
        if (!ALLOWED.contains(ext))
            throw new BusinessException("Chỉ chấp nhận JPG, PNG, WEBP, HEIC.");

        try {
            String publicId = "damage_photos/session_" + uuid + "_" + System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    photo.getBytes(),
                    ObjectUtils.asMap(
                            "public_id",     publicId,
                            "resource_type", "image",
                            "overwrite",     false,
                            "quality",       "auto:good",
                            "fetch_format",  "auto"
                    )
            );

            String url = (String) result.get("secure_url");
            if (url == null) throw new BusinessException("Upload thất bại — Cloudinary không trả URL.");

            log.info("Session {} uploaded photo: {}", uuid, url);

            // 3. Complete session directly
            uploadSessionService.completeSession(uuid, url);

            return ResponseEntity.ok(ApiResponse.success("Upload và đồng bộ thành công.", null));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Session {} upload failed: {}", uuid, e.getMessage(), e);
            throw new BusinessException("Không thể upload ảnh: " + e.getMessage());
        }
    }

    /**
     * POST /v1/attachments/upload
     * Body: multipart/form-data, field name = "photo"
     * Response: { success: true, data: { url: "https://..." } }
     */
    @Operation(summary = "Upload ảnh bằng chứng", description = "Tải ảnh từ PC/Server trực tiếp lên Cloudinary (bắt buộc JWT auth là QL kho hoặc Bảo vệ).")
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(
            @Parameter(description = "File ảnh bằng chứng (multipart/form-data)") @RequestParam("photo") MultipartFile photo,
            Authentication auth) {

        if (photo == null || photo.isEmpty())
            throw new BusinessException("Vui lòng chọn ảnh.");
        if (photo.getSize() > MAX_SIZE)
            throw new BusinessException("Ảnh quá lớn. Tối đa 15MB.");

        String filename = photo.getOriginalFilename() != null
                ? photo.getOriginalFilename().toLowerCase() : "";
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1) : "";
        if (!ALLOWED.contains(ext))
            throw new BusinessException("Chỉ chấp nhận JPG, PNG, WEBP, HEIC.");

        try {
            String userId = auth != null ? auth.getName() : "anon";
            String publicId = "damage_photos/" + userId + "_" + System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    photo.getBytes(),
                    ObjectUtils.asMap(
                            "public_id",     publicId,
                            "resource_type", "image",
                            "overwrite",     false,
                            "quality",       "auto:good",
                            "fetch_format",  "auto"
                    )
            );

            String url = (String) result.get("secure_url");
            if (url == null) throw new BusinessException("Upload thất bại — Cloudinary không trả URL.");

            log.info("Attachment uploaded by {}: {}", userId, url);
            return ResponseEntity.ok(ApiResponse.success("Upload thành công.", Map.of("url", url)));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Attachment upload failed: {}", e.getMessage(), e);
            throw new BusinessException("Không thể upload ảnh: " + e.getMessage());
        }
    }
}