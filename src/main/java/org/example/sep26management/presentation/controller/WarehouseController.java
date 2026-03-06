package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.WarehouseResponse;
import org.example.sep26management.infrastructure.persistence.entity.WarehouseEntity;
import org.example.sep26management.infrastructure.persistence.repository.WarehouseJpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * WarehouseController — Cung cấp danh sách kho cho FE dùng trong dropdown khi tạo Internal Transfer.
 * FE KHÔNG cần biết warehouseId — chỉ cần hiển thị warehouseName và gửi warehouseCode lên BE.
 */
@RestController
@RequestMapping("/v1/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouses (Lookup)", description = "Danh sách Kho Hàng dùng cho dropdown khi tạo đơn chuyển kho (Internal Transfer). "
        + "FE hiển thị warehouseName cho người dùng chọn, sau đó gửi warehouseCode lên BE — "
        + "BE tự resolve ra warehouseId nội bộ, FE không cần quan tâm đến ID.")
public class WarehouseController {

    private final WarehouseJpaRepository warehouseRepository;

    /**
     * GET /v1/warehouses — Lấy danh sách kho cho dropdown
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Danh sách kho hàng (Dropdown)",
            description = "Lấy danh sách tất cả kho hàng đang active để hiển thị dropdown khi tạo Internal Transfer.\n\n"
                    + "Cách FE sử dụng:\n"
                    + "1. Gọi API này để load dropdown chọn Kho Đích.\n"
                    + "2. Hiển thị `warehouseName` cho người dùng thấy và chọn.\n"
                    + "3. Khi người dùng chọn xong, lấy `warehouseCode` của item đó.\n"
                    + "4. Gửi `warehouseCode` vào field `Body.destinationWarehouseCode` khi gọi API tạo lệnh xuất.\n\n"
                    + "Lưu ý: FE KHÔNG cần truyền `warehouseId` (số ID) vào đâu cả — BE tự xử lý.\n"
                    + "Kho nguồn (source warehouse) cũng được lấy tự động từ JWT token của người dùng đang đăng nhập."
    )
    public ApiResponse<List<WarehouseResponse>> listWarehouses() {
        List<WarehouseResponse> result = warehouseRepository.findAll().stream()
                .filter(w -> Boolean.TRUE.equals(w.getActive()))
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("OK", result);
    }

    private WarehouseResponse toResponse(WarehouseEntity w) {
        return WarehouseResponse.builder()
                .warehouseId(w.getWarehouseId())
                .warehouseCode(w.getWarehouseCode())
                .warehouseName(w.getWarehouseName())
                .address(w.getAddress())
                .active(w.getActive())
                .build();
    }
}