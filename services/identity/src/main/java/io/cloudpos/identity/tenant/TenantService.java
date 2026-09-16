package io.cloudpos.identity.tenant;

import io.cloudpos.identity.store.Store;
import io.cloudpos.identity.store.StoreRepository;
import io.cloudpos.ids.Ids;
import io.cloudpos.web.ApiException;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenants;
    private final StoreRepository stores;

    public TenantService(TenantRepository tenants, StoreRepository stores) {
        this.tenants = tenants;
        this.stores = stores;
    }

    @Transactional
    public Tenant provision(UUID tenantId, String tenantName, String storeName,
                            String timezone, LocalTime businessDayCutoff) {
        Tenant tenant = tenants.save(Tenant.create(tenantId, tenantName));
        stores.save(Store.create(Ids.newId(), tenantId, storeName, timezone, businessDayCutoff));
        return tenant;
    }

    @Transactional
    public Tenant suspend(UUID tenantId) {
        Tenant tenant = require(tenantId);
        tenant.suspend();
        return tenant;
    }

    @Transactional
    public Tenant activate(UUID tenantId) {
        Tenant tenant = require(tenantId);
        tenant.activate();
        return tenant;
    }

    @Transactional(readOnly = true)
    public Tenant get(UUID tenantId) {
        return require(tenantId);
    }

    private Tenant require(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "No tenant with id " + tenantId));
    }
}
