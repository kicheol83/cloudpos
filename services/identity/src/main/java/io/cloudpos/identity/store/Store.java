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

    protected Store() {
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
}
