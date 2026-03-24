package com.oct.invoicesystem.domain.user.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record AssignRoleRequest(
        @NotEmpty List<UUID> roleIds
) {}
