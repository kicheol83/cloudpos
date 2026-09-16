package io.cloudpos.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ClaimsHeaderFilterTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();
    private static final UUID SHIFT = UUID.randomUUID();

    private final ClaimsHeaderFilter filter = new ClaimsHeaderFilter();

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(STAFF.toString())
                .claim("tid", TENANT.toString())
                .claim("sid", STORE.toString())
                .claim("did", DEVICE.toString())
                .claim("role", "WAITER")
                .claim("name", "Richardo")
                .claim("shift", SHIFT.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }

    @Test
    void injectsIdentityHeadersFromTheToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/stores"));

        var captured = new ServerWebExchange[1];
        Mono<Void> result = filter.filter(exchange, downstream -> {
            captured[0] = downstream;
            return Mono.empty();
        }).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                Mono.just(new SecurityContextImpl(new JwtAuthenticationToken(jwt())))));

        StepVerifier.create(result).verifyComplete();

        var headers = captured[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo(TENANT.toString());
        assertThat(headers.getFirst("X-Store-Id")).isEqualTo(STORE.toString());
        assertThat(headers.getFirst("X-Staff-Id")).isEqualTo(STAFF.toString());
        assertThat(headers.getFirst("X-Staff-Role")).isEqualTo("WAITER");
        assertThat(headers.getFirst("X-Shift-Id")).isEqualTo(SHIFT.toString());
        assertThat(headers.getFirst("X-Device-Id")).isEqualTo(DEVICE.toString());
    }

    @Test
    void stripsClientSuppliedTenantHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/stores")
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("X-Staff-Role", "OWNER"));

        var captured = new ServerWebExchange[1];
        Mono<Void> result = filter.filter(exchange, downstream -> {
            captured[0] = downstream;
            return Mono.empty();
        }).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                Mono.just(new SecurityContextImpl(new JwtAuthenticationToken(jwt())))));

        StepVerifier.create(result).verifyComplete();

        var headers = captured[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo(TENANT.toString());
        assertThat(headers.getFirst("X-Staff-Role")).isEqualTo("WAITER");
    }

    @Test
    void stripsIdentityHeadersWhenThereIsNoToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/auth/login")
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("X-Staff-Id", UUID.randomUUID().toString()));

        var captured = new ServerWebExchange[1];
        Mono<Void> result = filter.filter(exchange, downstream -> {
            captured[0] = downstream;
            return Mono.empty();
        });

        StepVerifier.create(result).verifyComplete();

        var headers = captured[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-Tenant-Id")).isNull();
        assertThat(headers.getFirst("X-Staff-Id")).isNull();
    }

    @Test
    void rejectsWhenPresentedDeviceDoesNotMatchTheTokenClaim() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/stores")
                        .header("X-Device-Id", UUID.randomUUID().toString()));

        Mono<Void> result = filter.filter(exchange, downstream -> Mono.empty())
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(new SecurityContextImpl(new JwtAuthenticationToken(jwt())))));

        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 403)
                .verify();
    }

    @Test
    void ignoresNonJwtAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header("X-Tenant-Id", UUID.randomUUID().toString()));

        var captured = new ServerWebExchange[1];
        Mono<Void> result = filter.filter(exchange, downstream -> {
            captured[0] = downstream;
            return Mono.empty();
        }).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                Mono.just(new SecurityContextImpl(
                        new TestingAuthenticationToken("someone", "creds")))));

        StepVerifier.create(result).verifyComplete();
        assertThat(captured[0].getRequest().getHeaders().getFirst("X-Tenant-Id")).isNull();
    }
}
