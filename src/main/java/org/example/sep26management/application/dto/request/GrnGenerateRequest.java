package org.example.sep26management.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrnGenerateRequest {

    @Schema(description = "Thông tin nhập Ngày SX / HSD cho các lô hàng chưa có")
    private List<ItemDateSync> itemDates;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDateSync {
        private Long receivingItemId;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}
