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
public class AttachmentController {

    private final Cloudinary cloudinary;

    private static final long   MAX_SIZE = 15L * 1024 * 1024; // 15 MB
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp", "heic", "heif");

    /**
     * POST /v1/attachments/upload
     * Body: multipart/form-data, field name = "photo"
     * Response: { success: true, data: { url: "https://..." } }
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(
            @RequestParam("photo") MultipartFile photo,
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