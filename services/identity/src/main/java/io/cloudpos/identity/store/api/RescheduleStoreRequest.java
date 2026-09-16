package io.cloudpos.identity.store.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record RescheduleStoreRequest(
        @NotBlank String timezone,
        @NotNull LocalTime business_day_cutoff) {
}
