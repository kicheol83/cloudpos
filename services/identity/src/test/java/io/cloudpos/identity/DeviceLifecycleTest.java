package io.cloudpos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cloudpos.identity.device.Device;
import io.cloudpos.identity.device.DevicePairingHandler;
import io.cloudpos.identity.device.DeviceService;
import io.cloudpos.identity.device.DeviceStatus;
import io.cloudpos.identity.store.Store;
import io.cloudpos.identity.store.StoreService;
import io.cloudpos.identity.tenant.Tenant;
import io.cloudpos.identity.tenant.TenantProvisioner;
import io.cloudpos.tenancy.TenantContext;
import io.cloudpos.web.ApiException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
class DeviceLifecycleTest {

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
    private DeviceService devices;

    @Autowired
    private DevicePairingHandler pairingHandler;

    private Tenant tenantA;
    private Tenant tenantB;
    private Store storeA;

    @BeforeEach
    void provisionTenants() throws Exception {
        try (Connection c = ownerConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM device_pairing");
            st.execute("DELETE FROM device");
            st.execute("DELETE FROM store");
            st.execute("DELETE FROM tenant");
        }
        tenantA = provisioner.provision("Tenant A", "Gangnam", "Asia/Seoul", LocalTime.of(5, 0));
        TenantContext.set(tenantA.id());
        storeA = stores.list().getFirst();

        tenantB = provisioner.provision("Tenant B", "Hongdae", "Asia/Seoul", LocalTime.of(5, 0));
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void registeredDeviceStartsPendingAndPairsOnce() {
        TenantContext.set(tenantA.id());
        DeviceService.Registration registration = devices.register(storeA.id(), "Counter 1");

        assertThat(registration.device().status()).isEqualTo(DeviceStatus.PENDING);
        assertThat(registration.pairingCode()).hasSize(8);

        TenantContext.clear();
        DevicePairingHandler.PairedDevice paired = pairingHandler.pair(registration.pairingCode());

        assertThat(paired.tenantId()).isEqualTo(tenantA.id());
        assertThat(paired.storeId()).isEqualTo(storeA.id());
        assertThat(paired.secret()).isNotBlank();

        TenantContext.set(tenantA.id());
        assertThat(devices.get(paired.deviceId()).status()).isEqualTo(DeviceStatus.ACTIVE);
    }

    @Test
    void pairingCodeCannotBeUsedTwice() {
        TenantContext.set(tenantA.id());
        DeviceService.Registration registration = devices.register(storeA.id(), "Counter 2");

        TenantContext.clear();
        pairingHandler.pair(registration.pairingCode());

        assertThatThrownBy(() -> pairingHandler.pair(registration.pairingCode()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void unknownPairingCodeIsRejected() {
        TenantContext.clear();
        assertThatThrownBy(() -> pairingHandler.pair("ZZZZZZZZ"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not recognised");
    }

    @Test
    void revokedDeviceFailsAuthentication() {
        TenantContext.set(tenantA.id());
        DeviceService.Registration registration = devices.register(storeA.id(), "Counter 3");

        TenantContext.clear();
        DevicePairingHandler.PairedDevice paired = pairingHandler.pair(registration.pairingCode());

        TenantContext.set(tenantA.id());
        assertThat(devices.authenticate(paired.deviceId(), paired.secret()).isActive()).isTrue();

        devices.revoke(paired.deviceId());

        assertThatThrownBy(() -> devices.authenticate(paired.deviceId(), paired.secret()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void wrongSecretIsRejected() {
        TenantContext.set(tenantA.id());
        DeviceService.Registration registration = devices.register(storeA.id(), "Counter 4");

        TenantContext.clear();
        DevicePairingHandler.PairedDevice paired = pairingHandler.pair(registration.pairingCode());

        TenantContext.set(tenantA.id());
        assertThatThrownBy(() -> devices.authenticate(paired.deviceId(), "not-the-secret"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void anotherTenantCannotSeeTheDevice() {
        TenantContext.set(tenantA.id());
        DeviceService.Registration registration = devices.register(storeA.id(), "Counter 5");

        TenantContext.set(tenantB.id());
        assertThat(devices.list()).isEmpty();
        assertThatThrownBy(() -> devices.get(registration.device().id()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void revokedDeviceDisappearsFromListing() {
        TenantContext.set(tenantA.id());
        DeviceService.Registration registration = devices.register(storeA.id(), "Counter 6");
        assertThat(devices.list()).hasSize(1);

        devices.revoke(registration.device().id());
        assertThat(devices.list()).isEmpty();
    }

    private static Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
