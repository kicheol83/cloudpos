package io.cloudpos.identity.auth;

import io.cloudpos.identity.staff.StaffRole;
import java.util.UUID;

public record StaffSession(UUID tenantId, UUID storeId, UUID staffId, UUID deviceId,
                           String displayName, StaffRole role) {
}
