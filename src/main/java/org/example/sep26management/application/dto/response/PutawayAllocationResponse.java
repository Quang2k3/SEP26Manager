package org.example.sep26management.application.dto.response;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayAllocationResponse {

    @Schema(description = "ID phân bổ")
    private Long allocationId;

    @Schema(description = "ID Putaway Task")
    private Long putawayTaskId;

    @Schema(description = "ID SKU")
    private Long skuId;

    @Schema(description = "Mã SKU")
    private String skuCode;

    @Schema(description = "Tên SKU")
    private String skuName;

    @Schema(description = "ID Lot")
    private Long lotId;

    @Schema(description = "ID Bin đích")
    private Long locationId;

    @Schema(description = "Mã Bin đích")
    private String locationCode;

    @Schema(description = "Số lượng phân bổ")
    private BigDecimal allocatedQty;

    @Schema(description = "Trạng thái: RESERVED, CONFIRMED, CANCELLED")
    private String status;

    @Schema(description = "Người phân bổ")
    private Long allocatedBy;

    @Schema(description = "Thời gian phân bổ")
    private LocalDateTime allocatedAt;
}
