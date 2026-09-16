package io.cloudpos.identity.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "store")
public class Store {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "business_day_cutoff", nullable = false)
    private LocalTime businessDayCutoff;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Store() {
    }

    private Store(UUID id, UUID tenantId, String name, String timezone, LocalTime cutoff) {
        Instant now = Instant.now();
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.timezone = timezone;
        this.businessDayCutoff = cutoff;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Store create(UUID id, UUID tenantId, String name,
                               String timezone, LocalTime cutoff) {
        return new Store(id, tenantId, name, timezone, cutoff);
    }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void reschedule(String timezone, LocalTime cutoff) {
        this.timezone = timezone;
        this.businessDayCutoff = cutoff;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public String timezone() {
        return timezone;
    }

    public LocalTime businessDayCutoff() {
        return businessDayCutoff;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }
}
