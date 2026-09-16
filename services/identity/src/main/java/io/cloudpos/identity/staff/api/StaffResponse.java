package io.cloudpos.identity.staff.api;

import io.cloudpos.identity.staff.Staff;
import java.time.LocalDate;
import java.util.UUID;

public record StaffResponse(UUID id, UUID store_id, String employee_code, String display_name,
                            String role, String employment_status, LocalDate joined_on) {

    public static StaffResponse from(Staff staff) {
        return new StaffResponse(staff.id(), staff.storeId(), staff.employeeCode(),
                staff.displayName(), staff.role().name(),
                staff.employmentStatus().name(), staff.joinedOn());
    }
}
