package io.cloudpos.identity.staff;

import io.cloudpos.identity.security.PinEncoder;
import io.cloudpos.identity.store.StoreService;
import io.cloudpos.ids.Ids;
import io.cloudpos.tenancy.TenantContext;
import io.cloudpos.web.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

    private final StaffRepository staff;
    private final StoreService stores;
    private final PinEncoder pins;

    public StaffService(StaffRepository staff, StoreService stores, PinEncoder pins) {
        this.staff = staff;
        this.stores = stores;
        this.pins = pins;
    }

    @Transactional(readOnly = true)
    public List<Staff> listByStore(UUID storeId) {
        return staff.findAllByStoreIdAndDeletedAtIsNullOrderByDisplayNameAsc(storeId);
    }

    @Transactional(readOnly = true)
    public Staff get(UUID staffId) {
        return require(staffId);
    }

    @Transactional
    public Staff hire(UUID storeId, String employeeCode, String displayName,
                      StaffRole role, String pin, LocalDate joinedOn) {
        stores.get(storeId);

        if (staff.existsByStoreIdAndEmployeeCodeAndDeletedAtIsNull(storeId, employeeCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_CODE_TAKEN",
                    "Employee code " + employeeCode + " is already in use at this store");
        }

        return staff.save(Staff.hire(Ids.newId(), TenantContext.require(), storeId,
                employeeCode, displayName, role, pins.encode(pin), joinedOn));
    }

    @Transactional
    public Staff changeRole(UUID staffId, StaffRole role) {
        Staff member = require(staffId);
        member.assignRole(role);
        return member;
    }

    @Transactional
    public void changePin(UUID staffId, String currentPin, String newPin) {
        Staff member = require(staffId);
        if (!pins.matches(currentPin, member.pinHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "PIN_INVALID",
                    "Current PIN is incorrect");
        }
        member.changePin(pins.encode(newPin));
    }

    @Transactional
    public void resetPin(UUID staffId, String newPin) {
        require(staffId).changePin(pins.encode(newPin));
    }

    @Transactional
    public Staff suspend(UUID staffId) {
        Staff member = require(staffId);
        member.suspend();
        return member;
    }

    @Transactional
    public Staff reinstate(UUID staffId) {
        Staff member = require(staffId);
        member.reinstate();
        return member;
    }

    @Transactional
    public void terminate(UUID staffId) {
        require(staffId).terminate();
    }

    @Transactional
    public PinVerification verifyPin(UUID staffId, UUID deviceStoreId, String pin) {
        Staff member = require(staffId);

        if (!member.storeId().equals(deviceStoreId)) {
            return PinVerification.WRONG_STORE;
        }
        if (!member.isEmployed()) {
            return PinVerification.NOT_EMPLOYED;
        }
        if (member.isLocked(Instant.now())) {
            return PinVerification.LOCKED;
        }
        if (!pins.matches(pin, member.pinHash())) {
            member.recordFailedPin();
            return member.isLocked(Instant.now())
                    ? PinVerification.LOCKED
                    : PinVerification.WRONG_PIN;
        }

        member.recordSuccessfulPin();
        return PinVerification.OK;
    }

    private Staff require(UUID staffId) {
        return staff.findByIdAndDeletedAtIsNull(staffId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND", "No staff member with id " + staffId));
    }
}
