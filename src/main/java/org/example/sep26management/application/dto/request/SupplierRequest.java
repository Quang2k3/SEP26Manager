package org.example.sep26management.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupplierRequest {

    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    @Size(max = 300, message = "Tên nhà cung cấp tối đa 300 ký tự")
    @Schema(description = "Tên nhà cung cấp", example = "Công ty TNHH Samsung Việt Nam")
    private String supplierName;

    @Size(max = 50, message = "Mã số thuế tối đa 50 ký tự")
    @Schema(description = "Mã số thuế", example = "0101234567")
    private String taxCode;

    @Size(max = 255, message = "Email tối đa 255 ký tự")
    @Schema(description = "Email liên hệ", example = "contact@samsung.com")
    private String email;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    @Schema(description = "Số điện thoại", example = "0123456789")
    private String phone;

    @Schema(description = "Địa chỉ", example = "123 Lê Lợi, Q.1, TP.HCM")
    private String address;
}