package io.cloudpos.identity.staff.api;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull String role) {
}
