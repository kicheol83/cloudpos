package io.cloudpos.identity.device;

import io.cloudpos.identity.security.Secrets;
import io.cloudpos.tenancy.TenantContext;
import io.cloudpos.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DevicePairingHandler {

    private final DevicePairingRepository pairings;
    private final DeviceService devices;

    public DevicePairingHandler(DevicePairingRepository pairings, DeviceService devices) {
        this.pairings = pairings;
        this.devices = devices;
    }

    public record PairedDevice(UUID deviceId, UUID tenantId, UUID storeId, String secret) {
    }

    public PairedDevice pair(String pairingCode) {
        DevicePairing pairing = pairings.findByCodeHash(Secrets.sha256(pairingCode))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAIRING_NOT_FOUND",
                        "Pairing code not recognised"));

        TenantContext.set(pairing.tenantId());

        DeviceService.PairingResult result =
                devices.completePairing(pairing.id(), pairing.deviceId());

        Device device = result.device();
        return new PairedDevice(device.id(), device.tenantId(), device.storeId(), result.secret());
    }
}
