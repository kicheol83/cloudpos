package io.cloudpos.identity.shift;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    Optional<Shift> findByStaffIdAndStatus(UUID staffId, ShiftStatus status);

    List<Shift> findAllByStoreIdAndBusinessDateOrderByOpenedAtAsc(UUID storeId,
                                                                  LocalDate businessDate);
}
