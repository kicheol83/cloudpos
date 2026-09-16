package io.cloudpos.identity.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device")
public class Device {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus status;

    @Column(name = "secret_hash")
    private String secretHash;

    @Column(name = "paired_at")
    private Instant pairedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Device() {
    }

    private Device(UUID id, UUID tenantId, UUID storeId, String label) {
        Instant now = Instant.now();
        this.id = id;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.label = label;
        this.status = DeviceStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Device register(UUID id, UUID tenantId, UUID storeId, String label) {
        return new Device(id, tenantId, storeId, label);
    }

    public void completePairing(String secretHash) {
        this.secretHash = secretHash;
        this.status = DeviceStatus.ACTIVE;
        this.pairedAt = Instant.now();
        this.updatedAt = this.pairedAt;
    }

    public void revoke() {
        this.status = DeviceStatus.REVOKED;
        this.secretHash = null;
        this.revokedAt = Instant.now();
        this.updatedAt = this.revokedAt;
    }

    public void touch() {
        this.lastSeenAt = Instant.now();
    }

    public boolean isActive() {
        return status == DeviceStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID storeId() {
        return storeId;
    }

    public String label() {
        return label;
    }

    public DeviceStatus status() {
        return status;
    }

    public String secretHash() {
        return secretHash;
    }

    public Instant pairedAt() {
        return pairedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
