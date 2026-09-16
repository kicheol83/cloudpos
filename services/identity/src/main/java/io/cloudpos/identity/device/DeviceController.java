package io.cloudpos.identity.device;

import io.cloudpos.identity.device.api.DeviceCredentialResponse;
import io.cloudpos.identity.device.api.DeviceRegistrationResponse;
import io.cloudpos.identity.device.api.DeviceResponse;
import io.cloudpos.identity.device.api.PairDeviceRequest;
import io.cloudpos.identity.device.api.RegisterDeviceRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/v1/devices")
public class DeviceController {

    private final DeviceService devices;
    private final DevicePairingHandler pairingHandler;

    public DeviceController(DeviceService devices, DevicePairingHandler pairingHandler) {
        this.devices = devices;
        this.pairingHandler = pairingHandler;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public List<DeviceResponse> list() {
        return devices.list().stream().map(DeviceResponse::from).toList();
    }

    @GetMapping("/{deviceId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public DeviceResponse get(@PathVariable UUID deviceId) {
        return DeviceResponse.from(devices.get(deviceId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public DeviceRegistrationResponse register(@Valid @RequestBody RegisterDeviceRequest request) {
        return DeviceRegistrationResponse.from(
                devices.register(request.store_id(), request.label()));
    }

    @PostMapping("/pair")
    public DeviceCredentialResponse pair(@Valid @RequestBody PairDeviceRequest request) {
        return DeviceCredentialResponse.from(pairingHandler.pair(request.pairing_code()));
    }

    @PostMapping("/{deviceId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public void revoke(@PathVariable UUID deviceId) {
        devices.revoke(deviceId);
    }
}
