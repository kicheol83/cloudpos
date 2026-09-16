package io.cloudpos.identity.store;

import io.cloudpos.identity.store.api.CreateStoreRequest;
import io.cloudpos.identity.store.api.RenameStoreRequest;
import io.cloudpos.identity.store.api.RescheduleStoreRequest;
import io.cloudpos.identity.store.api.StoreResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stores")
public class StoreController {

    private final StoreService stores;

    public StoreController(StoreService stores) {
        this.stores = stores;
    }

    @GetMapping
    public List<StoreResponse> list() {
        return stores.list().stream().map(StoreResponse::from).toList();
    }

    @GetMapping("/{storeId}")
    public StoreResponse get(@PathVariable UUID storeId) {
        return StoreResponse.from(stores.get(storeId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse create(@Valid @RequestBody CreateStoreRequest request) {
        return StoreResponse.from(stores.create(
                request.name(), request.timezoneOrDefault(), request.cutoffOrDefault()));
    }

    @PatchMapping("/{storeId}/name")
    public StoreResponse rename(@PathVariable UUID storeId,
                                @Valid @RequestBody RenameStoreRequest request) {
        return StoreResponse.from(stores.rename(storeId, request.name()));
    }

    @PatchMapping("/{storeId}/schedule")
    public StoreResponse reschedule(@PathVariable UUID storeId,
                                    @Valid @RequestBody RescheduleStoreRequest request) {
        return StoreResponse.from(stores.reschedule(
                storeId, request.timezone(), request.business_day_cutoff()));
    }

    @DeleteMapping("/{storeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID storeId) {
        stores.delete(storeId);
    }
}
