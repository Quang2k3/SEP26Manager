package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.example.sep26management.domain.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeStatusRequest {

    @Schema(description = "Trạng thái mới", example = "INACTIVE")
    @NotNull(message = "Status is required")
    private UserStatus status;

    // Optional: Suspension end date (null = permanent suspension)
    @Schema(description = "Khóa tài khoản đến ngày (chỉ dành cho Locked)", example = "2026-05-20")
    private LocalDate suspendUntil;

    // Optional: Reason for status change
    @Schema(description = "Lý do thay đổi trạng thái", example = "Nhân viên nghỉ việc")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
