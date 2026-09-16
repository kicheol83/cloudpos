package io.cloudpos.identity.auth;

import io.cloudpos.identity.staff.StaffRole;
import java.util.UUID;

public record StaffSession(UUID tenantId, UUID storeId, UUID staffId, UUID deviceId,
                           UUID shiftId, String displayName, StaffRole role) {

    public StaffSession withShift(UUID shiftId) {
        return new StaffSession(tenantId, storeId, staffId, deviceId, shiftId,
                displayName, role);
    }
}
