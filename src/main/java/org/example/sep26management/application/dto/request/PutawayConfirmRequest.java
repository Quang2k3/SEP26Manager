package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayConfirmRequest {

    @Schema(description = "Danh sách các sản phẩm và vị trí cất được xác nhận thực tế")
    @NotEmpty(message = "items must not be empty")
    @Valid
    private List<PutawayItemConfirm> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PutawayItemConfirm {
        @Schema(description = "ID Dòng sản phẩm trong Putaway Task", example = "1")
        @NotNull
        private Long putawayTaskItemId;

        @Schema(description = "ID Vị trí (Bin) cất vào thực tế", example = "10")
        @NotNull
        private Long locationId;

        @Schema(description = "Lượng hàng thực tế cất vào", example = "50")
        @NotNull
        @Positive
        private BigDecimal qty;
    }
}
