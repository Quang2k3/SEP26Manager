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
 * PickSignedNoteService — Upload ảnh phiếu lấy hàng đã ký của nhân viên kho.
 *
 * Flow: In phiếu lấy hàng (Pick List PDF) → nhân viên ký tay
 *       → scan QR bằng điện thoại → chụp ảnh
 *       → POST /outbound/sales-orders/{soId}/pick-signed-note
 *       → lưu URL vào sales_orders.pick_signed_note_url
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PickSignedNoteService {

    private final SalesOrderJpaRepository salesOrderRepository;
    private final Cloudinary cloudinary;
    // [FIX REALTIME] Thêm NotificationService để push event cho QC khi upload phiếu ký
    private final NotificationService notificationService;

    private static final List<String> ALLOWED = Arrays.asList("jpg", "jpeg", "png", "webp", "heic");
    private static final long MAX_SIZE = 15 * 1024 * 1024; // 15MB

    public ApiResponse<Map<String, String>> uploadPickSignedNote(Long soId, MultipartFile file) {
        SalesOrderEntity so = salesOrderRepository.findById(soId)
                .orElseThrow(() -> new BusinessException("Sales Order không tồn tại: " + soId));

        // Cho phép upload ở bước PICKING, QC_SCAN, QC_PASSED, DISPATCHED
        // Sau khi COMPLETED thì không cho thay đổi
        if ("COMPLETED".equals(so.getStatus())) {
            throw new BusinessException(
                    "Đơn hàng đã COMPLETED — không thể thay đổi ảnh chữ ký phiếu lấy hàng.");
        }
        String st = so.getStatus();
        if (!java.util.Set.of("PICKING","QC_SCAN","QC_PASSED","DISPATCHED","ON_HOLD").contains(st)) {
            throw new BusinessException(
                    "Chỉ có thể upload ảnh phiếu lấy hàng khi đơn đang ở bước lấy hàng hoặc sau đó. Hiện tại: " + st);
        }

        if (file == null || file.isEmpty())
            throw new BusinessException("Vui lòng chọn ảnh phiếu lấy hàng đã ký.");
        if (file.getSize() > MAX_SIZE)
            throw new BusinessException("Ảnh quá lớn. Tối đa 15MB.");

        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1) : "";
        if (!ALLOWED.contains(ext))
            throw new BusinessException("Chỉ chấp nhận ảnh JPG, PNG, WEBP, HEIC.");

        try {
            String publicId = "pick_signed_notes/pick_SO_" + soId + "_" + System.currentTimeMillis();

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

            so.setPickSignedNoteUrl(url);
            so.setPickSignedNoteUploadedAt(LocalDateTime.now());
            salesOrderRepository.save(so);

            log.info("Pick signed note uploaded for soId={}: {}", soId, url);

            // ── [FIX REALTIME] Notify QC + MANAGER + KEEPER để list/modal refresh ────
            // QC cần nhận event này để biết phiếu ký đã sẵn sàng → hiển thị ảnh trong modal
            // và có thể bắt đầu bước QC tiếp theo mà không cần F5.
            try {
                notificationService.notifyRoles(
                        new String[]{"QC", "MANAGER", "KEEPER"},
                        "outbound_pick_signed_note_uploaded",
                        soId, so.getSoCode(),
                        "Đã upload phiếu lấy hàng có chữ ký — QC có thể kiểm định");
            } catch (Exception ex) {
                // Không để WS failure phá vỡ transaction upload chính
                log.warn("WS notify pick_signed_note_uploaded failed (soId={}): {}", soId, ex.getMessage());
            }

            return ApiResponse.success("Đã lưu ảnh phiếu lấy hàng thành công.", Map.of(
                    "soId",   String.valueOf(soId),
                    "soCode", so.getSoCode(),
                    "url",    url
            ));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload pick signed note failed for soId={}: {}", soId, e.getMessage(), e);
            throw new BusinessException("Không thể upload ảnh: " + e.getMessage());
        }
    }
}