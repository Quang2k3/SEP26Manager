package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayAllocateRequest {

    @Schema(description = "Danh sách phân bổ hàng vào các bin")
    @NotEmpty(message = "items must not be empty")
    @Valid
    private List<AllocateItem> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AllocateItem {
        @Schema(description = "ID SKU cần cất", example = "3")
        @NotNull
        private Long skuId;

        @Schema(description = "ID Bin đích", example = "10")
        @NotNull
        private Long locationId;

        @Schema(description = "Số lượng phân bổ vào bin này", example = "50")
        @NotNull
        @Positive
        private BigDecimal qty;
    }
}
