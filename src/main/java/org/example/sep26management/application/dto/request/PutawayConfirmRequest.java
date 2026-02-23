package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayConfirmRequest {

    /** list of items to confirm, each with chosen location */
    @NotEmpty(message = "items must not be empty")
    @Valid
    private List<PutawayItemConfirm> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PutawayItemConfirm {
        @NotNull
        private Long putawayTaskItemId;
        @NotNull
        private Long locationId;
        @NotNull
        @Positive
        private BigDecimal qty;
    }
}
