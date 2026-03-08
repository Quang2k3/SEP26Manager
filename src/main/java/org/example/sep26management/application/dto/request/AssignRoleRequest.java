package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.domain.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignRoleRequest {

    @Schema(description = "Phân Quyền mới (Role)", example = "MANAGER")
    @NotNull(message = "Role is required")
    private UserRole role;
}