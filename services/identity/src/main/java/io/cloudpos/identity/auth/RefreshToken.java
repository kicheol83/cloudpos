package io.cloudpos.identity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    protected RefreshToken() {
    }

    private RefreshToken(UUID id, UUID tenantId, UUID familyId, UUID staffId,
                         UUID deviceId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.familyId = familyId;
        this.staffId = staffId;
        this.deviceId = deviceId;
        this.tokenHash = tokenHash;
        this.issuedAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(UUID id, UUID tenantId, UUID familyId, UUID staffId,
                                     UUID deviceId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(id, tenantId, familyId, staffId, deviceId, tokenHash, expiresAt);
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }

    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public UUID id() {
        return id;
    }

    public UUID familyId() {
        return familyId;
    }

    public UUID staffId() {
        return staffId;
    }

    public UUID deviceId() {
        return deviceId;
    }

    public String revokedReason() {
        return revokedReason;
    }
}
