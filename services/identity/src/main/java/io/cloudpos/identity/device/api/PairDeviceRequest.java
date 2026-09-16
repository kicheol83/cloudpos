package io.cloudpos.identity.device.api;

import jakarta.validation.constraints.NotBlank;

public record PairDeviceRequest(@NotBlank String pairing_code) {
}
