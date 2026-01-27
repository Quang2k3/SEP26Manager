package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.domain.enums.UserStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserStatusRequest {

    @NotNull(message = "Status is required")
    private UserStatus status;
}