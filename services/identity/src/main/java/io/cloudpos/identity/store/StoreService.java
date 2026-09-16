package io.cloudpos.identity.store;

import io.cloudpos.ids.Ids;
import io.cloudpos.tenancy.TenantContext;
import io.cloudpos.web.ApiException;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreRepository stores;

    public StoreService(StoreRepository stores) {
        this.stores = stores;
    }

    @Transactional(readOnly = true)
    public List<Store> list() {
        return stores.findAllByDeletedAtIsNullOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Store get(UUID storeId) {
        return require(storeId);
    }

    @Transactional
    public Store create(String name, String timezone, LocalTime businessDayCutoff) {
        return stores.save(Store.create(
                Ids.newId(), TenantContext.require(), name, timezone, businessDayCutoff));
    }

    @Transactional
    public Store rename(UUID storeId, String name) {
        Store store = require(storeId);
        store.rename(name);
        return store;
    }

    @Transactional
    public Store reschedule(UUID storeId, String timezone, LocalTime businessDayCutoff) {
        Store store = require(storeId);
        store.reschedule(timezone, businessDayCutoff);
        return store;
    }

    @Transactional
    public void delete(UUID storeId) {
        require(storeId).markDeleted();
    }

    private Store require(UUID storeId) {
        return stores.findByIdAndDeletedAtIsNull(storeId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "No store with id " + storeId));
    }
}
