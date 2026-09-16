package io.cloudpos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cloudpos.identity.auth.AuthHandler;
import io.cloudpos.identity.device.DevicePairingHandler;
import io.cloudpos.identity.device.DeviceService;
import io.cloudpos.identity.shift.BusinessDay;
import io.cloudpos.identity.shift.Shift;
import io.cloudpos.identity.shift.ShiftService;
import io.cloudpos.identity.shift.ShiftStatus;
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
import java.time.Instant;
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
class ShiftLifecycleTest {

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
    private ShiftService shifts;

    @Autowired
    private AuthHandler auth;

    private Tenant tenant;
    private Store store;
    private Staff waiter;
    private DevicePairingHandler.PairedDevice device;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = ownerConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM shift");
            st.execute("DELETE FROM refresh_token");
            st.execute("DELETE FROM device_directory");
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
        TenantContext.clear();
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void loginOpensAShiftAndPutsItInTheToken() {
        AuthHandler.Tokens tokens = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        assertThat(tokens.session().shiftId()).isNotNull();

        TenantContext.set(tenant.id());
        Shift shift = shifts.get(tokens.session().shiftId());
        assertThat(shift.status()).isEqualTo(ShiftStatus.OPEN);
        assertThat(shift.staffId()).isEqualTo(waiter.id());
    }

    @Test
    void loggingInTwiceResumesTheSameShift() {
        AuthHandler.Tokens first = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");
        AuthHandler.Tokens second = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        assertThat(second.session().shiftId()).isEqualTo(first.session().shiftId());
    }

    @Test
    void closingAShiftAllowsANewOneToOpen() {
        AuthHandler.Tokens first = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        TenantContext.set(tenant.id());
        shifts.close(first.session().shiftId());
        TenantContext.clear();

        AuthHandler.Tokens second = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        assertThat(second.session().shiftId()).isNotEqualTo(first.session().shiftId());
    }

    @Test
    void closingATwiceClosedShiftIsRejected() {
        AuthHandler.Tokens tokens = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        TenantContext.set(tenant.id());
        shifts.close(tokens.session().shiftId());

        assertThatThrownBy(() -> shifts.close(tokens.session().shiftId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void businessDateFallsBackToThePreviousDayBeforeCutoff() {
        LocalDate date = BusinessDay.of(
                Instant.parse("2026-06-11T18:30:00Z"), "Asia/Seoul", LocalTime.of(5, 0));

        assertThat(date).isEqualTo(LocalDate.of(2026, 6, 11));

        LocalDate lateNight = BusinessDay.of(
                Instant.parse("2026-06-11T18:00:00Z"), "Asia/Seoul", LocalTime.of(5, 0));

        assertThat(lateNight).isEqualTo(LocalDate.of(2026, 6, 11));

        LocalDate beforeCutoff = BusinessDay.of(
                Instant.parse("2026-06-11T19:30:00Z"), "Asia/Seoul", LocalTime.of(5, 0));

        assertThat(beforeCutoff).isEqualTo(LocalDate.of(2026, 6, 11));
    }

    @Test
    void shiftIsListedForItsBusinessDate() {
        AuthHandler.Tokens tokens = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        TenantContext.set(tenant.id());
        Shift shift = shifts.get(tokens.session().shiftId());

        assertThat(shifts.listByBusinessDate(store.id(), shift.businessDate()))
                .extracting(Shift::id)
                .containsExactly(shift.id());
    }

    private static Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
