package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.example.sep26management.domain.enums.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeStatusRequest {

    @NotNull(message = "Status is required")
    private UserStatus status;

    // Optional: Suspension end date (null = permanent suspension)
    private LocalDate suspendUntil;

    // Optional: Reason for status change
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
