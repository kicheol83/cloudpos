package io.cloudpos.identity.store;

import java.util.List;
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
        return stores.findAll();
    }
}
