package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickListShortageRequest {

    @NotEmpty(message = "Danh sách hàng thiếu không được để trống")
    private List<ShortageItemDto> shortages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShortageItemDto {
        @NotNull(message = "Task item ID không được thiếu")
        private Long taskItemId;

        @NotNull(message = "SKU ID không được thiếu")
        private Long skuId;
        
        private String lotNumber;

        @NotNull(message = "Số lượng thiếu không được rỗng")
        private BigDecimal missingQty;
    }
}
