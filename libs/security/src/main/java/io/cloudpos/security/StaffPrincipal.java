package io.cloudpos.security;

import java.util.UUID;

public record StaffPrincipal(UUID staffId, UUID tenantId, UUID storeId, UUID deviceId,
                             UUID shiftId, String role, String displayName) {
}
