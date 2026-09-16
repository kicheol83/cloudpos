package io.cloudpos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cloudpos.identity.store.Store;
import io.cloudpos.identity.store.StoreService;
import io.cloudpos.tenancy.TenantContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
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
class TenantIsolationTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

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
    private StoreService stores;

    @BeforeEach
    void seedTenants() throws Exception {

        try (Connection c = ownerConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM store");
            st.execute("DELETE FROM tenant");
            st.execute("""
                    INSERT INTO tenant (id, name) VALUES
                      ('%s', 'Tenant A'),
                      ('%s', 'Tenant B')
                    """.formatted(TENANT_A, TENANT_B));
            st.execute("""
                    INSERT INTO store (tenant_id, id, name) VALUES
                      ('%s', gen_random_uuid(), 'Tenant A — Gangnam'),
                      ('%s', gen_random_uuid(), 'Tenant B — Hongdae')
                    """.formatted(TENANT_A, TENANT_B));
        }
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void readsOnlyOwnTenantRows() {
        TenantContext.set(TENANT_A);
        assertThat(stores.list())
                .extracting(Store::name)
                .containsExactly("Tenant A — Gangnam");

        TenantContext.set(TENANT_B);
        assertThat(stores.list())
                .extracting(Store::name)
                .containsExactly("Tenant B — Hongdae");
    }

    @Test
    void refusesToQueryWithoutATenant() {
        assertThatThrownBy(() -> stores.list())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant bound");
    }

    @Test
    void databaseItselfReturnsNothingWhenTenantUnset() throws Exception {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "cloudpos_app", "cloudpos");
             Statement st = c.createStatement()) {
            var rs = st.executeQuery("SELECT count(*) FROM store");
            rs.next();
            assertThat(rs.getLong(1)).isZero();
        }
    }

    private static Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
