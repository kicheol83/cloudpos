package io.cloudpos.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContext {

    private AuthContext() {
    }

    public static Optional<StaffPrincipal> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof StaffPrincipal p)) {
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public static StaffPrincipal require() {
        return current().orElseThrow(() ->
                new IllegalStateException("No authenticated staff in the current request"));
    }

    public static UUID staffId() {
        return require().staffId();
    }

    public static UUID storeId() {
        return require().storeId();
    }

    public static Optional<UUID> shiftId() {
        return current().map(StaffPrincipal::shiftId);
    }
}
