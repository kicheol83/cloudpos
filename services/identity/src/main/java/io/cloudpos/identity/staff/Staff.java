package io.cloudpos.identity.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "staff")
public class Staff {

    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false)
    private EmploymentStatus employmentStatus;

    @Column(name = "pin_hash", nullable = false)
    private String pinHash;

    @Column(name = "pin_failed_count", nullable = false)
    private int pinFailedCount;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Column(name = "joined_on")
    private LocalDate joinedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    protected Staff() {
    }

    private Staff(UUID id, UUID tenantId, UUID storeId, String employeeCode,
                  String displayName, StaffRole role, String pinHash, LocalDate joinedOn) {
        Instant now = Instant.now();
        this.id = id;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.employeeCode = employeeCode;
        this.displayName = displayName;
        this.role = role;
        this.employmentStatus = EmploymentStatus.ACTIVE;
        this.pinHash = pinHash;
        this.pinFailedCount = 0;
        this.joinedOn = joinedOn;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Staff hire(UUID id, UUID tenantId, UUID storeId, String employeeCode,
                             String displayName, StaffRole role, String pinHash,
                             LocalDate joinedOn) {
        return new Staff(id, tenantId, storeId, employeeCode, displayName, role,
                pinHash, joinedOn);
    }

    public void assignRole(StaffRole role) {
        this.role = role;
        this.updatedAt = Instant.now();
    }

    public void changePin(String pinHash) {
        this.pinHash = pinHash;
        this.pinFailedCount = 0;
        this.pinLockedUntil = null;
        this.updatedAt = Instant.now();
    }

    public void recordFailedPin() {
        this.pinFailedCount++;
        if (this.pinFailedCount >= MAX_PIN_ATTEMPTS) {
            this.pinLockedUntil = Instant.now().plus(LOCKOUT);
            this.pinFailedCount = 0;
        }
        this.updatedAt = Instant.now();
    }

    public void recordSuccessfulPin() {
        this.pinFailedCount = 0;
        this.pinLockedUntil = null;
        this.updatedAt = Instant.now();
    }

    public void suspend() {
        this.employmentStatus = EmploymentStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reinstate() {
        this.employmentStatus = EmploymentStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void terminate() {
        this.employmentStatus = EmploymentStatus.TERMINATED;
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public boolean isLocked(Instant now) {
        return pinLockedUntil != null && pinLockedUntil.isAfter(now);
    }

    public boolean isEmployed() {
        return employmentStatus == EmploymentStatus.ACTIVE;
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

    public String employeeCode() {
        return employeeCode;
    }

    public String displayName() {
        return displayName;
    }

    public StaffRole role() {
        return role;
    }

    public EmploymentStatus employmentStatus() {
        return employmentStatus;
    }

    public String pinHash() {
        return pinHash;
    }

    public int pinFailedCount() {
        return pinFailedCount;
    }

    public Instant pinLockedUntil() {
        return pinLockedUntil;
    }

    public LocalDate joinedOn() {
        return joinedOn;
    }
}
