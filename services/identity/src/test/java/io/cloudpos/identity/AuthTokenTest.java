package io.cloudpos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jwt.SignedJWT;
import io.cloudpos.identity.auth.AuthHandler;
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
class AuthTokenTest {

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
    private AuthHandler auth;

    private Tenant tenant;
    private Store store;
    private Staff waiter;
    private DevicePairingHandler.PairedDevice device;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = ownerConnection(); Statement st = c.createStatement()) {
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
    void loginIssuesSignedAccessTokenWithTenantClaims() throws Exception {
        AuthHandler.Tokens tokens = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        SignedJWT jwt = SignedJWT.parse(tokens.accessToken());
        var claims = jwt.getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo(waiter.id().toString());
        assertThat(claims.getStringClaim("tid")).isEqualTo(tenant.id().toString());
        assertThat(claims.getStringClaim("sid")).isEqualTo(store.id().toString());
        assertThat(claims.getStringClaim("did")).isEqualTo(device.deviceId().toString());
        assertThat(claims.getStringClaim("role")).isEqualTo("WAITER");
        assertThat(jwt.getHeader().getKeyID()).isNotBlank();
        assertThat(tokens.refreshToken()).startsWith(tenant.id().toString() + ".");
    }

    @Test
    void loginWithoutTenantHeaderResolvesTenantFromDeviceDirectory() {
        TenantContext.clear();
        AuthHandler.Tokens tokens = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        assertThat(tokens.session().tenantId()).isEqualTo(tenant.id());
    }

    @Test
    void refreshRotatesTheToken() {
        AuthHandler.Tokens first = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        AuthHandler.Tokens second = auth.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.session().staffId()).isEqualTo(waiter.id());
    }

    @Test
    void reusingAConsumedRefreshTokenRevokesTheWholeFamily() {
        AuthHandler.Tokens first = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");
        AuthHandler.Tokens second = auth.refresh(first.refreshToken());

        assertThatThrownBy(() -> auth.refresh(first.refreshToken()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already used");

        assertThatThrownBy(() -> auth.refresh(second.refreshToken()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void logoutRevokesTheFamily() {
        AuthHandler.Tokens tokens = auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234");

        auth.logout(tokens.refreshToken());

        assertThatThrownBy(() -> auth.refresh(tokens.refreshToken()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        assertThatThrownBy(() -> auth.refresh(tenant.id() + ".not-a-real-token"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not recognised");
    }

    @Test
    void malformedRefreshTokenIsRejected() {
        assertThatThrownBy(() -> auth.refresh("garbage"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not recognised");
    }

    @Test
    void revokedDeviceCannotLogIn() {
        TenantContext.set(tenant.id());
        devices.revoke(device.deviceId());
        TenantContext.clear();

        assertThatThrownBy(() -> auth.login(
                device.deviceId(), device.secret(), waiter.id(), "1234"))
                .isInstanceOf(ApiException.class);
    }

    private static Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
