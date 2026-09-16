package io.cloudpos.gateway;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ClaimsHeaderFilter implements GlobalFilter, Ordered {

    static final List<String> MANAGED_HEADERS = List.of(
            "X-Tenant-Id", "X-Store-Id", "X-Staff-Id", "X-Staff-Role",
            "X-Staff-Name", "X-Shift-Id");

    static final String DEVICE_HEADER = "X-Device-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> withClaims(exchange, jwt))
                .defaultIfEmpty(stripped(exchange))
                .flatMap(chain::filter);
    }

    private ServerWebExchange withClaims(ServerWebExchange exchange, Jwt jwt) {
        String deviceClaim = jwt.getClaimAsString("did");
        String presentedDevice = exchange.getRequest().getHeaders().getFirst(DEVICE_HEADER);

        if (presentedDevice != null && !presentedDevice.equals(deviceClaim)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Device identity does not match the access token");
        }

        return exchange.mutate()
                .request(request -> {
                    removeManaged(request);
                    setIfPresent(request, "X-Tenant-Id", jwt.getClaimAsString("tid"));
                    setIfPresent(request, "X-Store-Id", jwt.getClaimAsString("sid"));
                    setIfPresent(request, "X-Staff-Id", jwt.getSubject());
                    setIfPresent(request, "X-Staff-Role", jwt.getClaimAsString("role"));
                    setIfPresent(request, "X-Staff-Name", jwt.getClaimAsString("name"));
                    setIfPresent(request, "X-Shift-Id", jwt.getClaimAsString("shift"));
                    setIfPresent(request, DEVICE_HEADER, deviceClaim);
                })
                .build();
    }

    private ServerWebExchange stripped(ServerWebExchange exchange) {
        return exchange.mutate().request(this::removeManaged).build();
    }

    private void removeManaged(ServerHttpRequest.Builder request) {
        request.headers(headers -> {
            MANAGED_HEADERS.forEach(headers::remove);
            headers.remove(DEVICE_HEADER);
        });
    }

    private void setIfPresent(ServerHttpRequest.Builder request, String header, String value) {
        if (value != null && !value.isBlank()) {
            request.header(header, value);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
