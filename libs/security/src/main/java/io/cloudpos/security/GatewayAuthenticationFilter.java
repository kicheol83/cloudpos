package io.cloudpos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String staffId = request.getHeader(GatewayHeaders.STAFF_ID);
        String role = request.getHeader(GatewayHeaders.STAFF_ROLE);

        if (staffId != null && role != null) {
            try {
                StaffPrincipal principal = new StaffPrincipal(
                        UUID.fromString(staffId),
                        uuidOrNull(request.getHeader(GatewayHeaders.TENANT_ID)),
                        uuidOrNull(request.getHeader(GatewayHeaders.STORE_ID)),
                        uuidOrNull(request.getHeader(GatewayHeaders.DEVICE_ID)),
                        uuidOrNull(request.getHeader(GatewayHeaders.SHIFT_ID)),
                        role,
                        request.getHeader(GatewayHeaders.STAFF_NAME));

                var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed identity header");
                return;
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static UUID uuidOrNull(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
