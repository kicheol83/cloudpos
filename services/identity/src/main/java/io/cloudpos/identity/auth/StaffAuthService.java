package io.cloudpos.identity.auth;

import io.cloudpos.identity.device.Device;
import io.cloudpos.identity.device.DeviceService;
import io.cloudpos.identity.staff.PinVerification;
import io.cloudpos.identity.staff.Staff;
import io.cloudpos.identity.staff.StaffService;
import io.cloudpos.tenancy.TenantContext;
import io.cloudpos.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StaffAuthService {

    private final DeviceService devices;
    private final StaffService staff;

    public StaffAuthService(DeviceService devices, StaffService staff) {
        this.devices = devices;
        this.staff = staff;
    }

    public StaffSession authenticate(UUID deviceId, String deviceSecret,
                                     UUID staffId, String pin) {
        Device device = devices.authenticate(deviceId, deviceSecret);

        PinVerification verification = staff.verifyPin(staffId, device.storeId(), pin);
        switch (verification) {
            case OK -> { }
            case LOCKED -> throw new ApiException(HttpStatus.LOCKED, "PIN_LOCKED",
                    "Too many failed attempts. Try again later.");
            case NOT_EMPLOYED -> throw new ApiException(HttpStatus.FORBIDDEN, "STAFF_NOT_EMPLOYED",
                    "This staff member is not active");
            case WRONG_STORE -> throw new ApiException(HttpStatus.FORBIDDEN, "STAFF_WRONG_STORE",
                    "This staff member does not belong to the device's store");
            case WRONG_PIN -> throw new ApiException(HttpStatus.UNAUTHORIZED, "PIN_INVALID",
                    "Incorrect PIN");
        }

        Staff member = staff.get(staffId);
        return new StaffSession(TenantContext.require(), device.storeId(), member.id(),
                device.id(), member.displayName(), member.role());
    }
}
