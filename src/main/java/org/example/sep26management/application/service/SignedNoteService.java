package org.example.sep26management.application.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.persistence.entity.SalesOrderEntity;
import org.example.sep26management.infrastructure.persistence.repository.SalesOrderJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * SignedNoteService — Upload ảnh phiếu xuất kho đã ký.
 *
 * Flow: In phiếu → ký tay → scan QR bằng điện thoại
 *       → chụp ảnh → POST /outbound/sales-orders/{soId}/signed-note
 *       → lưu URL vào sales_orders.signed_note_url
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignedNoteService {

    private final SalesOrderJpaRepository salesOrderRepository;
    private final Cloudinary cloudinary;

    private static final List<String> ALLOWED = Arrays.asList("jpg", "jpeg", "png", "webp", "heic");
    private static final long MAX_SIZE = 15 * 1024 * 1024; // 15MB (ảnh điện thoại thường lớn)

    public ApiResponse<Map<String, String>> uploadSignedNote(Long soId, MultipartFile file) {
        // Validate SO tồn tại
        SalesOrderEntity so = salesOrderRepository.findById(soId)
                .orElseThrow(() -> new BusinessException("Sales Order không tồn tại: " + soId));

        // Validate file
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
            String publicId = "signed_notes/signed_SO_" + soId + "_" + System.currentTimeMillis();

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

            // Lưu vào DB
            so.setSignedNoteUrl(url);
            so.setSignedNoteUploadedAt(LocalDateTime.now());
            salesOrderRepository.save(so);

            log.info("Signed note uploaded for soId={}: {}", soId, url);

            return ApiResponse.success("Đã lưu ảnh phiếu ký thành công.", Map.of(
                    "soId",   String.valueOf(soId),
                    "soCode", so.getSoCode(),
                    "url",    url
            ));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload signed note failed for soId={}: {}", soId, e.getMessage(), e);
            throw new BusinessException("Không thể upload ảnh: " + e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> getSignedNote(Long soId) {
        SalesOrderEntity so = salesOrderRepository.findById(soId)
                .orElseThrow(() -> new BusinessException("Sales Order không tồn tại: " + soId));

        return ApiResponse.success("OK", Map.of(
                "soId",              String.valueOf(soId),
                "soCode",            so.getSoCode(),
                "signedNoteUrl",     so.getSignedNoteUrl() != null ? so.getSignedNoteUrl() : "",
                "uploadedAt",        so.getSignedNoteUploadedAt() != null
                        ? so.getSignedNoteUploadedAt().toString() : "",
                "hasSignedNote",     so.getSignedNoteUrl() != null
        ));
    }
}