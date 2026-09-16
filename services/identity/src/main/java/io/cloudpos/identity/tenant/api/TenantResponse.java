package io.cloudpos.identity.tenant.api;

import io.cloudpos.identity.tenant.Tenant;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String status, Instant created_at) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.id(), tenant.name(),
                tenant.status().name(), tenant.createdAt());
    }
}
