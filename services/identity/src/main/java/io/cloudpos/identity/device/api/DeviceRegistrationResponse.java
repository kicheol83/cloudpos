package io.cloudpos.identity.device.api;

import io.cloudpos.identity.device.DeviceService;
import java.time.Instant;
import java.util.UUID;

public record DeviceRegistrationResponse(UUID id, UUID store_id, String label,
                                         String pairing_code, Instant pairing_expires_at) {

    public static DeviceRegistrationResponse from(DeviceService.Registration registration) {
        return new DeviceRegistrationResponse(
                registration.device().id(),
                registration.device().storeId(),
                registration.device().label(),
                registration.pairingCode(),
                registration.expiresAt());
    }
}
