package io.cloudpos.identity.shift;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shift")
public class Shift {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "opening_float_amount", nullable = false)
    private long openingFloatAmount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status;

    @Version
    private long version;

    protected Shift() {
    }

    private Shift(UUID id, UUID tenantId, UUID storeId, UUID staffId, UUID deviceId,
                  LocalDate businessDate, long openingFloatAmount, String currency) {
        this.id = id;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.staffId = staffId;
        this.deviceId = deviceId;
        this.businessDate = businessDate;
        this.openedAt = Instant.now();
        this.openingFloatAmount = openingFloatAmount;
        this.currency = currency;
        this.status = ShiftStatus.OPEN;
    }

    public static Shift open(UUID id, UUID tenantId, UUID storeId, UUID staffId, UUID deviceId,
                             LocalDate businessDate, long openingFloatAmount, String currency) {
        return new Shift(id, tenantId, storeId, staffId, deviceId, businessDate,
                openingFloatAmount, currency);
    }

    public void close() {
        this.status = ShiftStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == ShiftStatus.OPEN;
    }

    public UUID id() {
        return id;
    }

    public UUID storeId() {
        return storeId;
    }

    public UUID staffId() {
        return staffId;
    }

    public UUID deviceId() {
        return deviceId;
    }

    public LocalDate businessDate() {
        return businessDate;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public long openingFloatAmount() {
        return openingFloatAmount;
    }

    public String currency() {
        return currency;
    }

    public ShiftStatus status() {
        return status;
    }
}
