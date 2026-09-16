package io.cloudpos.identity.auth;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceDirectoryRepository extends JpaRepository<DeviceDirectory, UUID> {
}
