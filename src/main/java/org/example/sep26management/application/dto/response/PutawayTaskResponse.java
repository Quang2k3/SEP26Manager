package org.example.sep26management.application.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayTaskResponse {

    private Long putawayTaskId;
    private Long warehouseId;
    private Long receivingId;
    private String status;
    private Long fromLocationId;
    private Long assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String note;
    private List<PutawayTaskItemDto> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PutawayTaskItemDto {
        private Long putawayTaskItemId;
        private Long skuId;
        private String skuCode;
        private String skuName;
        private Long lotId;
        private BigDecimal quantity;
        private BigDecimal putawayQty;
        private Long suggestedLocationId;
        private Long actualLocationId;
    }
}
