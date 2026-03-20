package org.example.sep26management.application.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskEntity;
import org.example.sep26management.infrastructure.persistence.repository.PutawayTaskJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * PutawaySignedNoteService — Upload ảnh phiếu cất hàng đã ký.
 *
 * Flow: In phiếu hướng dẫn → nhân viên + quản lý ký tay
 *       → scan QR bằng điện thoại → chụp ảnh
 *       → POST /v1/putaway-tasks/{taskId}/signed-note
 *       → lưu URL vào putaway_tasks.signed_note_url
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PutawaySignedNoteService {

    private final PutawayTaskJpaRepository putawayTaskRepo;
    private final Cloudinary cloudinary;

    private static final List<String> ALLOWED = Arrays.asList("jpg", "jpeg", "png", "webp", "heic");
    private static final long MAX_SIZE = 15 * 1024 * 1024; // 15MB

    public ApiResponse<Map<String, String>> uploadSignedNote(Long taskId, MultipartFile file) {
        PutawayTaskEntity task = putawayTaskRepo.findById(taskId)
                .orElseThrow(() -> new BusinessException("Putaway Task không tồn tại: " + taskId));

        if (file == null || file.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ảnh phiếu đã ký.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("Ảnh quá lớn. Tối đa 15MB.");
        }

        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1) : "";
        if (!ALLOWED.contains(ext)) {
            throw new BusinessException("Chỉ chấp nhận ảnh JPG, PNG, WEBP, HEIC.");
        }

        try {
            String publicId = "putaway_signed_notes/signed_PT_" + taskId + "_" + System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id",     publicId,
                            "resource_type", "image",
                            "overwrite",     true,
                            "quality",       "auto",
                            "fetch_format",  "auto"
                    )
            );

            String url = (String) result.get("secure_url");
            if (url == null) throw new BusinessException("Upload thất bại. Vui lòng thử lại.");

            // Lưu URL + thời điểm vào task
            task.setSignedNoteUrl(url);
            task.setSignedNoteUploadedAt(LocalDateTime.now());
            putawayTaskRepo.save(task);

            log.info("Putaway signed note uploaded for taskId={}: {}", taskId, url);

            return ApiResponse.success("Đã lưu ảnh phiếu ký thành công.", Map.of(
                    "taskId", String.valueOf(taskId),
                    "url",    url
            ));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload putaway signed note failed for taskId={}: {}", taskId, e.getMessage(), e);
            throw new BusinessException("Không thể upload ảnh: " + e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> getSignedNote(Long taskId) {
        PutawayTaskEntity task = putawayTaskRepo.findById(taskId)
                .orElseThrow(() -> new BusinessException("Putaway Task không tồn tại: " + taskId));

        return ApiResponse.success("OK", Map.of(
                "taskId",         String.valueOf(taskId),
                "signedNoteUrl",  task.getSignedNoteUrl() != null ? task.getSignedNoteUrl() : "",
                "uploadedAt",     task.getSignedNoteUploadedAt() != null
                        ? task.getSignedNoteUploadedAt().toString() : "",
                "hasSignedNote",  task.getSignedNoteUrl() != null
        ));
    }
}