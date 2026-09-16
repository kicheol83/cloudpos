package io.cloudpos.identity.staff.api;

import jakarta.validation.constraints.Pattern;

public record ChangePinRequest(
        @Pattern(regexp = "\\d{4,6}") String current_pin,
        @Pattern(regexp = "\\d{4,6}") String new_pin) {
}
