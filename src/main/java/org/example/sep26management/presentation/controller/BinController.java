package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.ConfigureBinCapacityRequest;
import org.example.sep26management.application.dto.response.*;
import org.example.sep26management.application.enums.OccupancyStatus;
import org.example.sep26management.application.service.BinService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BinController — Bin occupancy and capacity APIs
 *
 * SCRUM-277 GET /api/v1/bins/occupancy — View Bin Occupancy (MANAGER + KEEPER)
 * GET /api/v1/bins/{locationId}/occupancy — View Single Bin Detail
 * SCRUM-278 GET /api/v1/bins/search-empty — Search Empty Bin (MANAGER + KEEPER)
 * SCRUM-279 PATCH /api/v1/bins/{locationId}/capacity — Configure Bin Capacity
 * (MANAGER only)
 */
@RestController
@RequestMapping("/v1/bins")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Bin Management", description = "Quản lý bin (vị trí lưu trữ nhỏ nhất trong kệ). "
        + "Xem chiếm dụng (occupancy), tìm bin trống, cấu hình dung lượng bin (maxWeightKg, maxVolumeM3).")
public class BinController {

    private final BinService binService;

    // ─────────────────────────────────────────────────────────────
    // SCRUM-277: View Bin Occupancy (UC-LOC-06)
    // GET
    // /api/v1/bins/occupancy?warehouseId=1&zoneId=2&occupancyStatus=PARTIAL&page=0&size=20
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/occupancy")
    @PreAuthorize("hasAnyRole('MANAGER','KEEPER')")
    @Operation(summary = "Xem chiếm dụng các bin", description = "Lấy danh sách bin với thông tin occupancy (EMPTY/PARTIAL/FULL). "
            + "Lọc theo warehouseId, zoneId, occupancyStatus. Phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<BinOccupancyResponse>>> viewBinOccupancy(
            @RequestParam Long warehouseId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) OccupancyStatus occupancyStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(binService.viewBinOccupancy(
                warehouseId, zoneId, occupancyStatus, page, size));
    }

    /**
     * UC-LOC-06 3c: View detailed inventory in a specific bin
     * GET /api/v1/bins/{locationId}/occupancy
     */
    @GetMapping("/{locationId}/occupancy")
    @PreAuthorize("hasAnyRole('MANAGER','KEEPER')")
    @Operation(summary = "Chi tiết occupancy 1 bin", description = "Xem chi tiết inventory trong 1 bin cụ thể: danh sách SKU, số lượng, lot, tỉ lệ chiếm dụng.")
    public ResponseEntity<ApiResponse<BinOccupancyResponse>> getBinDetail(
            @PathVariable Long locationId) {

        return ResponseEntity.ok(binService.getBinDetail(locationId));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-278: Search Empty Bin (UC-LOC-07)
    // GET
    // /api/v1/bins/search-empty?warehouseId=1&zoneId=2&requiredWeightKg=50&requiredVolumeM3=0.5
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/search-empty")
    @PreAuthorize("hasAnyRole('MANAGER','KEEPER')")
    @Operation(summary = "Tìm bin trống", description = "Tìm các bin trống (không có inventory) trong warehouse/zone. "
            + "Có thể lọc theo yêu cầu dung lượng (requiredWeightKg, requiredVolumeM3).\n\n"
            + "**Data yêu cầu:** \n"
            + "- `Query.warehouseId` (Bắt buộc): ID của kho.\n"
            + "- `Query.zoneId` (Tùy chọn): Lọc theo ID khu vực.\n"
            + "- `Query.page` (Tùy chọn): Trang kết quả, mặc định 0.\n"
            + "- `Query.size` (Tùy chọn): Kích thước trang, mặc định 10.")
    public ResponseEntity<ApiResponse<PageResponse<EmptyBinResponse>>> searchEmptyBin(
            @RequestParam Long warehouseId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) BigDecimal requiredWeightKg,
            @RequestParam(required = false) BigDecimal requiredVolumeM3,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(binService.searchEmptyBin(
                warehouseId, zoneId, requiredWeightKg, requiredVolumeM3, page, size));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-279: Configure Bin Capacity (UC-LOC-08)
    // PATCH /api/v1/bins/{locationId}/capacity
    // ─────────────────────────────────────────────────────────────

    @PatchMapping("/{locationId}/capacity")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cấu hình dung lượng bin (Manager)", description = "Cập nhật maxWeightKg và maxVolumeM3 cho bin. Chỉ role MANAGER. Dùng để set giới hạn lưu trữ cho putaway suggestion.")
    public ResponseEntity<ApiResponse<BinCapacityResponse>> configureBinCapacity(
            @PathVariable Long locationId,
            @Valid @RequestBody ConfigureBinCapacityRequest request,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId();
        return ResponseEntity.ok(binService.configureBinCapacity(
                locationId, request, userId,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")));
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        Object details = auth.getDetails();
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) details;
            Object uid = map.get("userId");
            if (uid instanceof Long)
                return (Long) uid;
            if (uid instanceof Integer)
                return ((Integer) uid).longValue();
            if (uid != null)
                return Long.parseLong(uid.toString());
        }
        throw new RuntimeException(MessageConstants.USER_ID_NOT_FOUND);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty())
            return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty())
            return xri;
        return request.getRemoteAddr();
    }
}