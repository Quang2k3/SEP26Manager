package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupplierResponse {

    @Schema(description = "ID Hệ thống", example = "1")
    private Long supplierId;

    @Schema(description = "Mã NCC", example = "SUP-SAMSUNG")
    private String supplierCode;

    @Schema(description = "Tên NCC", example = "Công ty TNHH Samsung")
    private String supplierName;

    @Schema(description = "Mã số thuế", example = "0101234567")
    private String taxCode;

    @Schema(description = "Email", example = "contact@samsung.com")
    private String email;

    @Schema(description = "Điện thoại", example = "0123456789")
    private String phone;

    @Schema(description = "Địa chỉ", example = "123 Lê Lợi, Q.1, TP.HCM")
    private String address;

    @Schema(description = "Trạng thái hoạt động", example = "true")
    private Boolean active;

    @Schema(description = "Thời gian tạo")
    private LocalDateTime createdAt;

    @Schema(description = "Thời gian cập nhật cuối")
    private LocalDateTime updatedAt;
}