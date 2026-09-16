package io.cloudpos.identity.device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findAllByStatusNotOrderByLabelAsc(DeviceStatus status);

    Optional<Device> findByIdAndStatus(UUID id, DeviceStatus status);
}
