package io.cloudpos.identity.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record LoginRequest(
        @NotNull UUID device_id,
        @NotBlank String device_secret,
        @NotNull UUID staff_id,
        @Pattern(regexp = "\\d{4,6}") String pin) {
}
