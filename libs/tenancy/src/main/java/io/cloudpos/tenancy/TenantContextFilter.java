package io.cloudpos.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantContextFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Tenant-Id";

    private static final java.util.Set<String> UNSCOPED_PATHS =
            java.util.Set.of("/actuator/health", "/actuator/info", "/actuator/prometheus");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return UNSCOPED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String raw = request.getHeader(HEADER);
        try {
            if (raw != null && !raw.isBlank()) {
                TenantContext.set(UUID.fromString(raw));
            }
            chain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed " + HEADER);
        } finally {
            TenantContext.clear();
        }
    }
}
