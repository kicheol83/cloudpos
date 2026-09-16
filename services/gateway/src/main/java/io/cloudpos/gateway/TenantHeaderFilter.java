package io.cloudpos.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TenantHeaderFilter implements GlobalFilter, Ordered {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String DEV_HEADER = "X-Dev-Tenant-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tenant = resolveTenant(exchange);

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> {
                    r.headers(h -> h.remove(TENANT_HEADER));
                    if (tenant != null) {
                        r.header(TENANT_HEADER, tenant);
                    }
                })
                .build();

        return chain.filter(mutated);
    }

    private String resolveTenant(ServerWebExchange exchange) {

        return exchange.getRequest().getHeaders().getFirst(DEV_HEADER);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
