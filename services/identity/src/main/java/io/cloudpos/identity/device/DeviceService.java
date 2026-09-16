package io.cloudpos.identity.device;

import io.cloudpos.identity.auth.DeviceDirectory;
import io.cloudpos.identity.auth.DeviceDirectoryRepository;
import io.cloudpos.identity.security.Secrets;
import io.cloudpos.identity.store.StoreService;
import io.cloudpos.ids.Ids;
import io.cloudpos.tenancy.TenantContext;
import io.cloudpos.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private static final Duration PAIRING_VALIDITY = Duration.ofMinutes(15);

    private final DeviceRepository devices;
    private final DevicePairingRepository pairings;
    private final DeviceDirectoryRepository directory;
    private final StoreService stores;

    public DeviceService(DeviceRepository devices, DevicePairingRepository pairings,
                         DeviceDirectoryRepository directory, StoreService stores) {
        this.devices = devices;
        this.pairings = pairings;
        this.directory = directory;
        this.stores = stores;
    }

    public record Registration(Device device, String pairingCode, Instant expiresAt) {
    }

    public record PairingResult(Device device, String secret) {
    }

    @Transactional
    public Registration register(UUID storeId, String label) {
        stores.get(storeId);

        UUID tenantId = TenantContext.require();
        Device device = devices.save(Device.register(Ids.newId(), tenantId, storeId, label));
        directory.save(DeviceDirectory.of(device.id(), tenantId, storeId));

        String code = Secrets.pairingCode();
        Instant expiresAt = Instant.now().plus(PAIRING_VALIDITY);
        pairings.save(DevicePairing.issue(
                Ids.newId(), Secrets.sha256(code), tenantId, device.id(), expiresAt));

        return new Registration(device, code, expiresAt);
    }

    @Transactional
    public PairingResult completePairing(UUID pairingId, UUID deviceId) {
        DevicePairing pairing = pairings.findById(pairingId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "PAIRING_NOT_FOUND", "Pairing no longer available"));

        if (!pairing.isUsable(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "PAIRING_EXPIRED",
                    "Pairing code has expired or was already used");
        }

        Device device = devices.findById(deviceId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "No device with id " + deviceId));

        if (device.status() == DeviceStatus.REVOKED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEVICE_REVOKED",
                    "This device has been revoked");
        }

        String secret = Secrets.randomSecret();
        device.completePairing(Secrets.sha256(secret));
        pairing.consume();
        return new PairingResult(device, secret);
    }

    @Transactional(readOnly = true)
    public List<Device> list() {
        return devices.findAllByStatusNotOrderByLabelAsc(DeviceStatus.REVOKED);
    }

    @Transactional(readOnly = true)
    public Device get(UUID deviceId) {
        return require(deviceId);
    }

    @Transactional
    public void revoke(UUID deviceId) {
        require(deviceId).revoke();
        directory.deleteById(deviceId);
    }

    @Transactional
    public Device authenticate(UUID deviceId, String secret) {
        Device device = require(deviceId);
        if (!device.isActive() || device.secretHash() == null
                || !Secrets.matches(secret, device.secretHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "DEVICE_CREDENTIAL_INVALID",
                    "Device credential rejected");
        }
        device.touch();
        return device;
    }

    private Device require(UUID deviceId) {
        return devices.findById(deviceId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "No device with id " + deviceId));
    }
}
