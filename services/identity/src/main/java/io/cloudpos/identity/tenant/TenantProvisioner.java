package io.cloudpos.identity.tenant;

import io.cloudpos.ids.Ids;
import io.cloudpos.tenancy.TenantContext;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantProvisioner {

    private final TenantService tenants;

    public TenantProvisioner(TenantService tenants) {
        this.tenants = tenants;
    }

    public Tenant provision(String tenantName, String storeName,
                            String timezone, LocalTime businessDayCutoff) {
        UUID tenantId = Ids.newId();
        TenantContext.set(tenantId);
        return tenants.provision(tenantId, tenantName, storeName, timezone, businessDayCutoff);
    }
}
