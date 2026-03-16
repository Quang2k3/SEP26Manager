package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.CustomerResponse;
import org.example.sep26management.infrastructure.persistence.entity.CustomerEntity;
import org.example.sep26management.infrastructure.persistence.repository.CustomerJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
     * POST /v1/customers — Tạo khách hàng mới, tự gen customerCode
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Tạo khách hàng mới",
            description = "Tạo khách hàng mới với customerName, email, phone (tuỳ chọn).\n\n"
                    + "- `customerCode` được **tự động sinh** theo format `CUST-YYYYMMDD-NNNN` — FE không cần truyền.\n"
                    + "- Response trả về `customerCode` để FE dùng ngay khi tạo Sales Order."
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {

        // Sinh customerCode: CUST-YYYYMMDD-NNNN
        String date = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = customerRepository.count() + 1;
        String code = String.format("CUST-%s-%04d", date, count);

        // Đảm bảo unique (nếu trùng do race condition, thêm random suffix)
        while (customerRepository.findByCustomerCode(code).isPresent()) {
            code = String.format("CUST-%s-%04d", date,
                    (long)(Math.random() * 9000) + 1000);
        }

        CustomerEntity entity = CustomerEntity.builder()
                .customerCode(code)
                .customerName(request.getCustomerName().trim())
                .email(request.getEmail())
                .phone(request.getPhone())
                .active(true)
                .build();

        CustomerEntity saved = customerRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo khách hàng thành công", toResponse(saved)));
    }

    @Data
    public static class CreateCustomerRequest {
        @NotBlank(message = "customerName is required")
        private String customerName;
        private String email;
        private String phone;
    }

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