package io.cloudpos.identity.device.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegisterDeviceRequest(@NotNull UUID store_id, @NotBlank String label) {
}
