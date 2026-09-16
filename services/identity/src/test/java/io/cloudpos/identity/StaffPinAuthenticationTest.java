package io.cloudpos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cloudpos.identity.auth.StaffAuthService;
import io.cloudpos.identity.auth.StaffSession;
import io.cloudpos.identity.device.DevicePairingHandler;
import io.cloudpos.identity.device.DeviceService;
import io.cloudpos.identity.staff.Staff;
import io.cloudpos.identity.staff.StaffRole;
import io.cloudpos.identity.staff.StaffService;
import io.cloudpos.identity.store.Store;
import io.cloudpos.identity.store.StoreService;
import io.cloudpos.identity.tenant.Tenant;
import io.cloudpos.identity.tenant.TenantProvisioner;
import io.cloudpos.tenancy.TenantContext;
import io.cloudpos.web.ApiException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class StaffPinAuthenticationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("identity")
                    .withUsername("cloudpos_owner")
                    .withPassword("cloudpos")
                    .withReuse(true);

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "cloudpos_app");
        registry.add("spring.datasource.password", () -> "cloudpos");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void createApplicationRole() throws Exception {
        try (Connection c = ownerConnection(); Statement st = c.createStatement()) {
            st.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'cloudpos_app') THEN
                            CREATE ROLE cloudpos_app LOGIN PASSWORD 'cloudpos';
                        END IF;
                    END
                    $$;
                    """);
            st.execute("GRANT USAGE ON SCHEMA public TO cloudpos_app");
        }
    }

    @Autowired
    private TenantProvisioner provisioner;

    @Autowired
    private StoreService stores;

    @Autowired
    private StaffService staff;

    @Autowired
    private DeviceService devices;

    @Autowired
    private DevicePairingHandler pairingHandler;

    @Autowired
    private StaffAuthService auth;

    private Tenant tenant;
    private Store store;
    private Staff waiter;
    private DevicePairingHandler.PairedDevice device;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = ownerConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM device_pairing");
            st.execute("DELETE FROM device");
            st.execute("DELETE FROM staff");
            st.execute("DELETE FROM store");
            st.execute("DELETE FROM tenant");
        }

        tenant = provisioner.provision("Tenant A", "Gangnam", "Asia/Seoul", LocalTime.of(5, 0));
        TenantContext.set(tenant.id());
        store = stores.list().getFirst();

        waiter = staff.hire(store.id(), "W001", "Richardo", StaffRole.WAITER,
                "1234", LocalDate.of(2026, 1, 5));

        DeviceService.Registration registration = devices.register(store.id(), "Counter 1");
        TenantContext.clear();
        device = pairingHandler.pair(registration.pairingCode());
        TenantContext.set(tenant.id());
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void authenticatesWithDeviceSecretAndPin() {
        StaffSession session = auth.authenticate(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        assertThat(session.staffId()).isEqualTo(waiter.id());
        assertThat(session.storeId()).isEqualTo(store.id());
        assertThat(session.role()).isEqualTo(StaffRole.WAITER);
        assertThat(session.displayName()).isEqualTo("Richardo");
    }

    @Test
    void rejectsWrongPin() {
        assertThatThrownBy(() -> auth.authenticate(
                device.deviceId(), device.secret(), waiter.id(), "9999"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Incorrect PIN");
    }

    @Test
    void rejectsWrongDeviceSecret() {
        assertThatThrownBy(() -> auth.authenticate(
                device.deviceId(), "wrong-secret", waiter.id(), "1234"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void locksAfterFiveFailedAttempts() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> auth.authenticate(
                    device.deviceId(), device.secret(), waiter.id(), "0000"))
                    .isInstanceOf(ApiException.class);
        }

        assertThat(staff.get(waiter.id()).pinLockedUntil()).isNotNull();

        assertThatThrownBy(() -> auth.authenticate(
                device.deviceId(), device.secret(), waiter.id(), "1234"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Too many failed attempts");
    }

    @Test
    void failedAttemptsPersistAcrossCalls() {
        assertThatThrownBy(() -> auth.authenticate(
                device.deviceId(), device.secret(), waiter.id(), "0000"))
                .isInstanceOf(ApiException.class);

        assertThat(staff.get(waiter.id()).pinFailedCount()).isEqualTo(1);
    }

    @Test
    void successfulLoginClearsFailedAttempts() {
        assertThatThrownBy(() -> auth.authenticate(
                device.deviceId(), device.secret(), waiter.id(), "0000"))
                .isInstanceOf(ApiException.class);

        auth.authenticate(device.deviceId(), device.secret(), waiter.id(), "1234");

        assertThat(staff.get(waiter.id()).pinFailedCount()).isZero();
    }

    @Test
    void suspendedStaffCannotAuthenticate() {
        staff.suspend(waiter.id());

        assertThatThrownBy(() -> auth.authenticate(
                device.deviceId(), device.secret(), waiter.id(), "1234"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void changingPinClearsLockout() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> auth.authenticate(
                    device.deviceId(), device.secret(), waiter.id(), "0000"))
                    .isInstanceOf(ApiException.class);
        }

        staff.resetPin(waiter.id(), "5678");

        StaffSession session = auth.authenticate(
                device.deviceId(), device.secret(), waiter.id(), "5678");
        assertThat(session.staffId()).isEqualTo(waiter.id());
    }

    @Test
    void employeeCodeIsUniquePerStore() {
        assertThatThrownBy(() -> staff.hire(store.id(), "W001", "Another",
                StaffRole.WAITER, "4321", LocalDate.now()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already in use");
    }

    private static Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
