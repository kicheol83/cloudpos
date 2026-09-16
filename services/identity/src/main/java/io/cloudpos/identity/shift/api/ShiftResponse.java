package io.cloudpos.identity.shift.api;

import io.cloudpos.identity.shift.Shift;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShiftResponse(UUID id, UUID store_id, UUID staff_id, UUID device_id,
                            LocalDate business_date, Instant opened_at, Instant closed_at,
                            long opening_float_amount, String currency, String status) {

    public static ShiftResponse from(Shift shift) {
        return new ShiftResponse(shift.id(), shift.storeId(), shift.staffId(), shift.deviceId(),
                shift.businessDate(), shift.openedAt(), shift.closedAt(),
                shift.openingFloatAmount(), shift.currency(), shift.status().name());
    }
}
