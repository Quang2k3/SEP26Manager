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
public class WarehouseResponse {

    @Schema(description = "ID Của Kho", example = "1")
    private Long warehouseId;
    @Schema(description = "Mã Kho Code", example = "WH-HN-01")
    private String warehouseCode;
    @Schema(description = "Tên Kho", example = "Kho Hà Nội 01")
    private String warehouseName;
    @Schema(description = "Địa Chỉ Kho", example = "123 Giải Phóng, Hà Nội")
    private String address;
    @Schema(description = "Trạng thái hoạt động", example = "true")
    private Boolean active;
}