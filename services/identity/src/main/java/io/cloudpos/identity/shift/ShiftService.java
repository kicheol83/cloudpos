package io.cloudpos.identity.shift;

import io.cloudpos.identity.store.Store;
import io.cloudpos.identity.store.StoreService;
import io.cloudpos.ids.Ids;
import io.cloudpos.tenancy.TenantContext;
import io.cloudpos.web.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftService {

    private final ShiftRepository shifts;
    private final StoreService stores;

    public ShiftService(ShiftRepository shifts, StoreService stores) {
        this.shifts = shifts;
        this.stores = stores;
    }

    @Transactional
    public Shift openOrResume(UUID storeId, UUID staffId, UUID deviceId, long openingFloat) {
        Optional<Shift> existing = shifts.findByStaffIdAndStatus(staffId, ShiftStatus.OPEN);
        if (existing.isPresent()) {
            return existing.get();
        }

        Store store = stores.get(storeId);
        LocalDate businessDate = BusinessDay.of(
                Instant.now(), store.timezone(), store.businessDayCutoff());

        return shifts.save(Shift.open(Ids.newId(), TenantContext.require(), storeId,
                staffId, deviceId, businessDate, openingFloat, "KRW"));
    }

    @Transactional(readOnly = true)
    public Optional<Shift> currentFor(UUID staffId) {
        return shifts.findByStaffIdAndStatus(staffId, ShiftStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public Shift get(UUID shiftId) {
        return require(shiftId);
    }

    @Transactional(readOnly = true)
    public List<Shift> listByBusinessDate(UUID storeId, LocalDate businessDate) {
        return shifts.findAllByStoreIdAndBusinessDateOrderByOpenedAtAsc(storeId, businessDate);
    }

    @Transactional
    public Shift close(UUID shiftId) {
        Shift shift = require(shiftId);
        if (!shift.isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIFT_ALREADY_CLOSED",
                    "This shift is already closed");
        }
        shift.close();
        return shift;
    }

    private Shift require(UUID shiftId) {
        return shifts.findById(shiftId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", "No shift with id " + shiftId));
    }
}
