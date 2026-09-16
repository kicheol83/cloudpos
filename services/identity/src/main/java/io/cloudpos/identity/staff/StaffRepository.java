package io.cloudpos.identity.staff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, UUID> {

    List<Staff> findAllByStoreIdAndDeletedAtIsNullOrderByDisplayNameAsc(UUID storeId);

    Optional<Staff> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByStoreIdAndEmployeeCodeAndDeletedAtIsNull(UUID storeId, String employeeCode);
}
