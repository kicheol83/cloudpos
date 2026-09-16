package io.cloudpos.identity.store.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalTime;

public record CreateStoreRequest(
        @NotBlank String name,
        String timezone,
        LocalTime business_day_cutoff) {

    public String timezoneOrDefault() {
        return timezone == null || timezone.isBlank() ? "Asia/Seoul" : timezone;
    }

    public LocalTime cutoffOrDefault() {
        return business_day_cutoff == null ? LocalTime.of(5, 0) : business_day_cutoff;
    }
}
