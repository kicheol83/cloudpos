package io.cloudpos.identity.shift;

import io.cloudpos.identity.shift.api.ShiftResponse;
import io.cloudpos.security.AuthContext;
import io.cloudpos.web.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/shifts")
public class ShiftController {

    private final ShiftService shifts;

    public ShiftController(ShiftService shifts) {
        this.shifts = shifts;
    }

    @GetMapping("/current")
    public ShiftResponse current() {
        return shifts.currentFor(AuthContext.staffId())
                .map(ShiftResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHIFT_NOT_OPEN",
                        "No open shift for this staff member"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public List<ShiftResponse> listByBusinessDate(
            @RequestParam UUID store_id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate business_date) {
        return shifts.listByBusinessDate(store_id, business_date).stream()
                .map(ShiftResponse::from).toList();
    }

    @PostMapping("/{shiftId}/close")
    public ShiftResponse close(@PathVariable UUID shiftId) {
        return ShiftResponse.from(shifts.close(shiftId));
    }
}
