package io.cloudpos.identity.staff;

import io.cloudpos.identity.staff.api.ChangePinRequest;
import io.cloudpos.identity.staff.api.ChangeRoleRequest;
import io.cloudpos.identity.staff.api.HireStaffRequest;
import io.cloudpos.identity.staff.api.StaffResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/staff")
public class StaffController {

    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping
    public List<StaffResponse> listByStore(@RequestParam UUID store_id) {
        return staff.listByStore(store_id).stream().map(StaffResponse::from).toList();
    }

    @GetMapping("/{staffId}")
    public StaffResponse get(@PathVariable UUID staffId) {
        return StaffResponse.from(staff.get(staffId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse hire(@Valid @RequestBody HireStaffRequest request) {
        return StaffResponse.from(staff.hire(
                request.store_id(),
                request.employee_code(),
                request.display_name(),
                StaffRole.valueOf(request.role()),
                request.pin(),
                request.joined_on()));
    }

    @PatchMapping("/{staffId}/role")
    public StaffResponse changeRole(@PathVariable UUID staffId,
                                    @Valid @RequestBody ChangeRoleRequest request) {
        return StaffResponse.from(staff.changeRole(staffId, StaffRole.valueOf(request.role())));
    }

    @PatchMapping("/{staffId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePin(@PathVariable UUID staffId,
                          @Valid @RequestBody ChangePinRequest request) {
        staff.changePin(staffId, request.current_pin(), request.new_pin());
    }

    @PostMapping("/{staffId}/suspend")
    public StaffResponse suspend(@PathVariable UUID staffId) {
        return StaffResponse.from(staff.suspend(staffId));
    }

    @PostMapping("/{staffId}/reinstate")
    public StaffResponse reinstate(@PathVariable UUID staffId) {
        return StaffResponse.from(staff.reinstate(staffId));
    }

    @PostMapping("/{staffId}/terminate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void terminate(@PathVariable UUID staffId) {
        staff.terminate(staffId);
    }
}
