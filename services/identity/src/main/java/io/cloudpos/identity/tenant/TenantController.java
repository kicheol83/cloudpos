package io.cloudpos.identity.tenant;

import io.cloudpos.identity.tenant.api.ProvisionTenantRequest;
import io.cloudpos.identity.tenant.api.TenantResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/platform/tenants")
public class TenantController {

    private final TenantProvisioner provisioner;
    private final TenantService tenants;

    public TenantController(TenantProvisioner provisioner, TenantService tenants) {
        this.provisioner = provisioner;
        this.tenants = tenants;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse provision(@Valid @RequestBody ProvisionTenantRequest request) {
        return TenantResponse.from(provisioner.provision(
                request.tenant_name(),
                request.store_name(),
                request.timezoneOrDefault(),
                request.cutoffOrDefault()));
    }

    @GetMapping("/{tenantId}")
    public TenantResponse get(@PathVariable UUID tenantId) {
        return TenantResponse.from(tenants.get(tenantId));
    }

    @PostMapping("/{tenantId}/suspend")
    public TenantResponse suspend(@PathVariable UUID tenantId) {
        return TenantResponse.from(tenants.suspend(tenantId));
    }

    @PostMapping("/{tenantId}/activate")
    public TenantResponse activate(@PathVariable UUID tenantId) {
        return TenantResponse.from(tenants.activate(tenantId));
    }
}
