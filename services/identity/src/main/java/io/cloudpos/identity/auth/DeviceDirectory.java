package io.cloudpos.identity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_directory")
public class DeviceDirectory {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeviceDirectory() {
    }

    private DeviceDirectory(UUID deviceId, UUID tenantId, UUID storeId) {
        this.deviceId = deviceId;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.createdAt = Instant.now();
    }

    public static DeviceDirectory of(UUID deviceId, UUID tenantId, UUID storeId) {
        return new DeviceDirectory(deviceId, tenantId, storeId);
    }

    public UUID deviceId() {
        return deviceId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID storeId() {
        return storeId;
    }
}
