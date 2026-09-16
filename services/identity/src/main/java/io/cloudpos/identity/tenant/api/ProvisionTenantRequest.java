package io.cloudpos.identity.tenant.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalTime;

public record ProvisionTenantRequest(
        @NotBlank String tenant_name,
        @NotBlank String store_name,
        String timezone,
        LocalTime business_day_cutoff) {

    public String timezoneOrDefault() {
        return timezone == null || timezone.isBlank() ? "Asia/Seoul" : timezone;
    }

    public LocalTime cutoffOrDefault() {
        return business_day_cutoff == null ? LocalTime.of(5, 0) : business_day_cutoff;
    }
}
