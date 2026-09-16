package io.cloudpos.identity.store.api;

import io.cloudpos.identity.store.Store;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record StoreResponse(UUID id, String name, String timezone,
                            LocalTime business_day_cutoff, Instant created_at) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(store.id(), store.name(), store.timezone(),
                store.businessDayCutoff(), store.createdAt());
    }
}
