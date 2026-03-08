package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZoneResponse {

    @Schema(description = "ID Zone", example = "5")
    private Long zoneId;
    @Schema(description = "ID Kho Hệ Thống", example = "1")
    private Long warehouseId;
    @Schema(description = "Mã Khu Vực", example = "ZONE-ELEC")
    private String zoneCode;
    @Schema(description = "Tên Khu Vực", example = "Khu Điện Tử")
    private String zoneName;
    @Schema(description = "Trạng Thái", example = "true")
    private Boolean active;
    @Schema(description = "Ngày Tạo", example = "2026-03-08T10:00:00")
    private LocalDateTime createdAt;
    @Schema(description = "Ngày Cập Nhật", example = "2026-03-08T10:00:00")
    private LocalDateTime updatedAt;
}