package io.cloudpos.identity.device.api;

import io.cloudpos.identity.device.Device;
import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(UUID id, UUID store_id, String label, String status,
                             Instant paired_at, Instant created_at) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(device.id(), device.storeId(), device.label(),
                device.status().name(), device.pairedAt(), device.createdAt());
    }
}
