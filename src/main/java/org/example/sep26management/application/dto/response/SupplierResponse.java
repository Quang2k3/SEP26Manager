package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupplierResponse {

    @Schema(description = "ID Hệ thống", example = "1")
    private Long supplierId;
    @Schema(description = "Mã", example = "SUP-SAMSUNG")
    private String supplierCode;
    @Schema(description = "Tên NCC", example = "Công ty TNHH Samsung")
    private String supplierName;
    @Schema(description = "Email", example = "contact@samsung.com")
    private String email;
    @Schema(description = "Điện thoại", example = "0123456789")
    private String phone;
    @Schema(description = "Hoạt động", example = "true")
    private Boolean active;
}