package io.cloudpos.identity.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record ApprovalRequest(
        @NotNull UUID approver_staff_id,
        @Pattern(regexp = "\\d{4,6}") String pin,
        @NotBlank String action) {
}
