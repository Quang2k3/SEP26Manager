package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RejectRequest {

    @Schema(description = "Lý do từ chối (tối thiểu 20 ký tự)",
            example = "Tồn kho không đủ để xuất, cần chờ lô nhập tiếp theo")
    @Size(min = 20, message = "Rejection reason must be at least 20 characters")
    private String reason;
}