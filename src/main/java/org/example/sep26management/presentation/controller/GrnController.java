package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.RejectRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.GrnResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.service.GrnService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/grns")
@RequiredArgsConstructor
@Tag(name = "Goods Receipt Note (GRN)", description = "Quản lý Phiếu nhập kho chính thức (GRN). GRN được tạo tự động từ Receiving Order sau khi kiểm đếm hoặc xử lý sự cố hoàn tất.")
public class GrnController {

    private final GrnService grnService;

    @GetMapping
    @Operation(summary = "Danh sách Phiếu nhập kho (List GRN)", description = "Lấy danh sách các phiếu GRN.")
    public ApiResponse<PageResponse<GrnResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return grnService.listGrns(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết GRN", description = "Xem chi tiết một GRN bao gồm danh sách các sản phẩm (SKU) PASS.")
    public ApiResponse<GrnResponse> get(@PathVariable Long id) {
        return grnService.getGrn(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt phiếu GRN (Manager)", description = "Manager xác nhận GRN hợp lệ, sẵn sàng để nhập kho. Chuyển từ PENDING_APPROVAL thành APPROVED.")
    public ApiResponse<GrnResponse> approve(@PathVariable Long id, Authentication auth) {
        return grnService.approve(id, extractUserId(auth));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Từ chối phiếu GRN (Manager)", description = "Manager từ chối GRN.")
    public ApiResponse<GrnResponse> reject(
            @PathVariable Long id,
            @RequestBody RejectRequest request,
            Authentication auth) {
        return grnService.reject(id, request.getReason(), extractUserId(auth));
    }

    @PostMapping("/{id}/post")
    @Operation(summary = "Thực thi Nhập Kho (Post GRN)", description = "Cất hàng vào Trạm Chờ (Staging), ghi nhận Inventory Transaction & Tự động tạo Task Xếp Kệ (Putaway). Chuyển thành POSTED.")
    public ApiResponse<GrnResponse> post(@PathVariable Long id, Authentication auth) {
        return grnService.post(id, extractUserId(auth));
    }

    private Long extractUserId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map) {
            Object uid = ((Map<?, ?>) auth.getDetails()).get("userId");
            if (uid instanceof Long)
                return (Long) uid;
            if (uid instanceof Integer)
                return ((Integer) uid).longValue();
        }
        throw new RuntimeException("Cannot extract userId from authentication");
    }
}
