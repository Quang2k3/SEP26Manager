package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.CustomerResponse;
import org.example.sep26management.infrastructure.persistence.entity.CustomerEntity;
import org.example.sep26management.infrastructure.persistence.repository.CustomerJpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CustomerController — Cung cấp dữ liệu Khách Hàng cho FE dùng trong dropdown khi tạo Sales Order.
 * FE KHÔNG cần biết customerId — chỉ cần hiển thị customerName và gửi customerCode lên BE.
 */
@RestController
@RequestMapping("/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers (Lookup)", description = "Danh sách Khách Hàng dùng cho dropdown khi tạo đơn xuất kho (Sales Order). "
        + "FE hiển thị customerName cho người dùng chọn, sau đó gửi customerCode lên BE — "
        + "BE tự resolve ra customerId nội bộ, FE không cần quan tâm đến ID.")
public class CustomerController {

    private final CustomerJpaRepository customerRepository;

    /**
     * GET /v1/customers — Lấy danh sách khách hàng cho dropdown
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Danh sách khách hàng (Dropdown)",
            description = "Lấy danh sách tất cả khách hàng đang active để hiển thị dropdown khi tạo Sales Order.\n\n"
                    + "Cách FE sử dụng:\n"
                    + "1. Gọi API này để load dropdown chọn Khách Hàng.\n"
                    + "2. Hiển thị `customerName` cho người dùng thấy và chọn.\n"
                    + "3. Khi người dùng chọn xong, lấy `customerCode` của item đó.\n"
                    + "4. Gửi `customerCode` vào field `Body.customerCode` khi gọi API tạo lệnh xuất.\n\n"
                    + "Lưu ý: FE KHÔNG cần truyền `customerId` vào đâu cả — BE tự xử lý."
    )
    public ApiResponse<List<CustomerResponse>> listCustomers() {
        List<CustomerResponse> result = customerRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("OK", result);
    }

    private CustomerResponse toResponse(CustomerEntity c) {
        return CustomerResponse.builder()
                .customerId(c.getCustomerId())
                .customerCode(c.getCustomerCode())
                .customerName(c.getCustomerName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .active(c.getActive())
                .build();
    }
}