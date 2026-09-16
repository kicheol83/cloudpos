package io.cloudpos.identity.device.api;

import io.cloudpos.identity.device.DevicePairingHandler;
import java.util.UUID;

public record DeviceCredentialResponse(UUID device_id, UUID tenant_id, UUID store_id,
                                       String device_secret) {

    public static DeviceCredentialResponse from(DevicePairingHandler.PairedDevice paired) {
        return new DeviceCredentialResponse(paired.deviceId(), paired.tenantId(),
                paired.storeId(), paired.secret());
    }
}
