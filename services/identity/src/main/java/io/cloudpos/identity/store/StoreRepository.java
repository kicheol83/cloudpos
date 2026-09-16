package io.cloudpos.identity.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    List<Store> findAllByDeletedAtIsNullOrderByNameAsc();

    Optional<Store> findByIdAndDeletedAtIsNull(UUID id);
}
