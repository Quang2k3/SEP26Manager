package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.SupplierRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.SupplierResponse;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.SupplierEntity;
import org.example.sep26management.infrastructure.persistence.repository.SupplierJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SupplierController — Quản lý Nhà Cung Cấp.
 *
 * GET    /v1/suppliers                    → Dropdown active (isAuthenticated)
 * GET    /v1/suppliers/manage             → Danh sách phân trang, tìm kiếm (MANAGER)
 * POST   /v1/suppliers                    → Tạo mới (MANAGER)
 * PUT    /v1/suppliers/{id}               → Cập nhật (MANAGER)
 * PATCH  /v1/suppliers/{id}/toggle-active → Bật/tắt (MANAGER)
 */
@RestController
@RequestMapping("/v1/suppliers")
@RequiredArgsConstructor
@Tag(name = "Suppliers", description = "Quản lý Nhà Cung Cấp. "
        + "GET /suppliers dùng cho dropdown (mọi role authenticated). "
        + "Các thao tác CRUD chỉ dành cho MANAGER.")
public class SupplierController {

    private final SupplierJpaRepository supplierRepository;

    // ── GET /v1/suppliers — Dropdown (all authenticated) ──────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Danh sách nhà cung cấp (Dropdown)",
            description = "Lấy tất cả nhà cung cấp đang active để hiển thị dropdown khi tạo GRN.\n\n"
                    + "FE hiển thị `supplierName`, gửi `supplierCode` lên BE khi tạo GRN. "
                    + "Để xem/quản lý tất cả, dùng `/v1/suppliers/manage` (MANAGER only)."
    )
    public ApiResponse<List<SupplierResponse>> listActiveSuppliers() {
        List<SupplierResponse> result = supplierRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("OK", result);
    }

    // ── GET /v1/suppliers/manage — Danh sách phân trang (MANAGER) ─────────────

    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('MANAGER')")
    @Operation(
            summary = "[MANAGER] Danh sách nhà cung cấp (phân trang + tìm kiếm)",
            description = "Danh sách nhà cung cấp hỗ trợ:\n"
                    + "- `keyword`: tìm theo tên hoặc mã NCC\n"
                    + "- `active`: true | false | bỏ trống = tất cả\n"
                    + "- `page` (từ 0), `size` (mặc định 10)\n"
                    + "- Sắp xếp mới nhất trước\n\n"
                    + "**Chỉ MANAGER.**"
    )
    public ApiResponse<Map<String, Object>> listSuppliersForManage(
            @Parameter(description = "Từ khoá tìm kiếm") @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc active") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Số trang (từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kích thước trang") @RequestParam(defaultValue = "10") int size
    ) {
        Page<SupplierEntity> pageResult = supplierRepository.searchSuppliers(
                keyword,
                active,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Map<String, Object> result = new HashMap<>();
        result.put("content", pageResult.getContent().stream().map(this::toResponse).collect(Collectors.toList()));
        result.put("currentPage", pageResult.getNumber());
        result.put("totalPages", pageResult.getTotalPages());
        result.put("totalElements", pageResult.getTotalElements());
        result.put("pageSize", pageResult.getSize());

        return ApiResponse.success("OK", result);
    }

    // ── POST /v1/suppliers — Tạo mới (MANAGER) ────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER')")
    @Operation(
            summary = "[MANAGER] Tạo nhà cung cấp mới",
            description = "`supplierCode` tự động sinh theo `SUP-YYYYMMDD-NNNN`. "
                    + "`supplierName` bắt buộc. Các trường còn lại tuỳ chọn.\n\n"
                    + "**Chỉ MANAGER.**"
    )
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(
            @Valid @RequestBody SupplierRequest request) {

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = supplierRepository.count() + 1;
        String code = String.format("SUP-%s-%04d", date, count);

        while (supplierRepository.existsBySupplierCode(code)) {
            code = String.format("SUP-%s-%04d", date, (long) (Math.random() * 9000) + 1000);
        }

        SupplierEntity entity = SupplierEntity.builder()
                .supplierCode(code)
                .supplierName(request.getSupplierName().trim())
                .taxCode(trim(request.getTaxCode()))
                .email(trim(request.getEmail()))
                .phone(trim(request.getPhone()))
                .address(trim(request.getAddress()))
                .active(true)
                .build();

        SupplierEntity saved = supplierRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo nhà cung cấp thành công", toResponse(saved)));
    }

    // ── PUT /v1/suppliers/{id} — Cập nhật (MANAGER) ───────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER')")
    @Operation(
            summary = "[MANAGER] Cập nhật thông tin nhà cung cấp",
            description = "Cập nhật `supplierName`, `taxCode`, `email`, `phone`, `address`. "
                    + "`supplierCode` không thể thay đổi sau khi tạo.\n\n"
                    + "**Chỉ MANAGER.**"
    )
    public ApiResponse<SupplierResponse> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {

        SupplierEntity entity = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp ID: " + id));

        entity.setSupplierName(request.getSupplierName().trim());
        entity.setTaxCode(trim(request.getTaxCode()));
        entity.setEmail(trim(request.getEmail()));
        entity.setPhone(trim(request.getPhone()));
        entity.setAddress(trim(request.getAddress()));

        SupplierEntity saved = supplierRepository.save(entity);
        return ApiResponse.success("Cập nhật nhà cung cấp thành công", toResponse(saved));
    }

    // ── PATCH /v1/suppliers/{id}/toggle-active — Bật/Tắt (MANAGER) ───────────

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('MANAGER')")
    @Operation(
            summary = "[MANAGER] Bật / Tắt hoạt động nhà cung cấp",
            description = "Toggle trạng thái active. "
                    + "Inactive → không xuất hiện ở dropdown GRN.\n\n"
                    + "**Chỉ MANAGER.**"
    )
    public ApiResponse<SupplierResponse> toggleActiveSupplier(@PathVariable Long id) {
        SupplierEntity entity = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp ID: " + id));

        boolean newActive = !Boolean.TRUE.equals(entity.getActive());
        entity.setActive(newActive);
        SupplierEntity saved = supplierRepository.save(entity);

        String msg = newActive ? "Đã kích hoạt nhà cung cấp" : "Đã vô hiệu hoá nhà cung cấp";
        return ApiResponse.success(msg, toResponse(saved));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SupplierResponse toResponse(SupplierEntity s) {
        return SupplierResponse.builder()
                .supplierId(s.getSupplierId())
                .supplierCode(s.getSupplierCode())
                .supplierName(s.getSupplierName())
                .taxCode(s.getTaxCode())
                .email(s.getEmail())
                .phone(s.getPhone())
                .address(s.getAddress())
                .active(s.getActive())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private String trim(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }
}