package io.cloudpos.identity.device;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevicePairingRepository extends JpaRepository<DevicePairing, UUID> {

    Optional<DevicePairing> findByCodeHash(String codeHash);
}
