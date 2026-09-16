package io.cloudpos.identity.store;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stores")
public class StoreController {

    private final StoreService stores;

    public StoreController(StoreService stores) {
        this.stores = stores;
    }

    public record StoreResponse(UUID id, String name, String timezone,
                                LocalTime business_day_cutoff, Instant created_at) {
    }

    @GetMapping
    public List<StoreResponse> list() {
        return stores.list().stream()
                .map(s -> new StoreResponse(s.id(), s.name(), s.timezone(),
                        s.businessDayCutoff(), s.createdAt()))
                .toList();
    }
}
