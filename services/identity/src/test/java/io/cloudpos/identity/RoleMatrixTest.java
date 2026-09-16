package io.cloudpos.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.cloudpos.identity.store.Store;
import io.cloudpos.identity.store.StoreService;
import io.cloudpos.identity.tenant.Tenant;
import io.cloudpos.identity.tenant.TenantProvisioner;
import io.cloudpos.tenancy.TenantContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RoleMatrixTest {

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
    private MockMvc mockMvc;

    @Autowired
    private TenantProvisioner provisioner;

    @Autowired
    private StoreService stores;

    private Tenant tenant;
    private Store store;

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
        TenantContext.clear();
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String role) {
        return builder
                .header("X-Tenant-Id", tenant.id().toString())
                .header("X-Store-Id", store.id().toString())
                .header("X-Staff-Id", UUID.randomUUID().toString())
                .header("X-Staff-Role", role)
                .header("X-Staff-Name", "Tester");
    }

    @Test
    void waiterCanListStores() throws Exception {
        mockMvc.perform(as(get("/v1/stores"), "WAITER")).andExpect(status().isOk());
    }

    @Test
    void waiterCannotCreateStores() throws Exception {
        mockMvc.perform(as(post("/v1/stores"), "WAITER")
                        .contentType("application/json")
                        .content("{\"name\":\"Yeouido\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanCreateStores() throws Exception {
        mockMvc.perform(as(post("/v1/stores"), "MANAGER")
                        .contentType("application/json")
                        .content("{\"name\":\"Yeouido\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void ownerCanHireStaff() throws Exception {
        mockMvc.perform(as(post("/v1/staff"), "OWNER")
                        .contentType("application/json")
                        .content("""
                                {"store_id":"%s","employee_code":"W010",
                                 "display_name":"New Hire","role":"WAITER","pin":"1234"}
                                """.formatted(store.id())))
                .andExpect(status().isCreated());
    }

    @Test
    void waiterCannotHireStaff() throws Exception {
        mockMvc.perform(as(post("/v1/staff"), "WAITER")
                        .contentType("application/json")
                        .content("""
                                {"store_id":"%s","employee_code":"W011",
                                 "display_name":"New Hire","role":"WAITER","pin":"1234"}
                                """.formatted(store.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void waiterCannotRegisterDevices() throws Exception {
        mockMvc.perform(as(post("/v1/devices"), "WAITER")
                        .contentType("application/json")
                        .content("{\"store_id\":\"%s\",\"label\":\"Counter 9\"}"
                                .formatted(store.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void kitchenCannotListDevices() throws Exception {
        mockMvc.perform(as(get("/v1/devices"), "KITCHEN")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/v1/stores")).andExpect(status().isUnauthorized());
    }

    @Test
    void authEndpointsStayPublic() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    private static Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
