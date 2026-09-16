package io.cloudpos.identity.staff.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

public record HireStaffRequest(
        @NotNull UUID store_id,
        @NotBlank String employee_code,
        @NotBlank String display_name,
        @NotNull String role,
        @Pattern(regexp = "\\d{4,6}") String pin,
        LocalDate joined_on) {
}
