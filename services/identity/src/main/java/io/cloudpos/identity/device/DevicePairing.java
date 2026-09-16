package io.cloudpos.identity.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_pairing")
public class DevicePairing {

    @Id
    private UUID id;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DevicePairing() {
    }

    private DevicePairing(UUID id, String codeHash, UUID tenantId, UUID deviceId, Instant expiresAt) {
        this.id = id;
        this.codeHash = codeHash;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public static DevicePairing issue(UUID id, String codeHash, UUID tenantId,
                                      UUID deviceId, Instant expiresAt) {
        return new DevicePairing(id, codeHash, tenantId, deviceId, expiresAt);
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID deviceId() {
        return deviceId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }
}
