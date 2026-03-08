package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.SupplierResponse;
import org.example.sep26management.infrastructure.persistence.entity.SupplierEntity;
import org.example.sep26management.infrastructure.persistence.repository.SupplierJpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SupplierController — Cung cấp dữ liệu Nhà Cung Cấp cho FE dùng trong dropdown.
 * FE KHÔNG cần biết supplierId — chỉ cần hiển thị supplierName và gửi supplierCode lên BE.
 */
@RestController
@RequestMapping("/v1/suppliers")
@RequiredArgsConstructor
@Tag(name = "Suppliers (Lookup)", description = "Danh sách Nhà Cung Cấp dùng cho dropdown chọn supplier khi tạo GRN. "
        + "FE hiển thị supplierName cho người dùng chọn, sau đó gửi supplierCode lên BE — "
        + "BE tự resolve ra supplierId nội bộ, FE không cần quan tâm đến ID.")
public class SupplierController {

    private final SupplierJpaRepository supplierRepository;

    /**
     * GET /v1/suppliers — Lấy danh sách supplier cho dropdown
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Danh sách nhà cung cấp (Dropdown)",
            description = "Lấy danh sách tất cả nhà cung cấp đang active để hiển thị dropdown khi tạo GRN.\n\n"
                    + "**Cách FE sử dụng:**\n"
                    + "1. Gọi API này để load dropdown chọn Nhà Cung Cấp.\n"
                    + "2. Hiển thị `supplierName` cho người dùng thấy và chọn.\n"
                    + "3. Khi người dùng chọn xong, lấy `supplierCode` của item đó.\n"
                    + "4. Gửi `supplierCode` vào field `Body.supplierCode` khi gọi API tạo GRN.\n\n"
                    + "👉 **Lưu ý:** FE **KHÔNG** cần truyền `supplierId` vào đâu cả — BE tự xử lý."
    )
    public ApiResponse<List<SupplierResponse>> listSuppliers() {
        List<SupplierResponse> result = supplierRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("OK", result);
    }

    private SupplierResponse toResponse(SupplierEntity s) {
        return SupplierResponse.builder()
                .supplierId(s.getSupplierId())
                .supplierCode(s.getSupplierCode())
                .supplierName(s.getSupplierName())
                .email(s.getEmail())
                .phone(s.getPhone())
                .active(s.getActive())
                .build();
    }
}