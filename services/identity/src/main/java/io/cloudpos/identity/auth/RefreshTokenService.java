package io.cloudpos.identity.auth;

import io.cloudpos.identity.security.JwtProperties;
import io.cloudpos.identity.security.Secrets;
import io.cloudpos.ids.Ids;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenRepository tokens, JwtProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    public record IssuedToken(String token, UUID familyId) {
    }

    public record RotationResult(RotationOutcome outcome, String token, UUID familyId,
                                 UUID staffId, UUID deviceId) {

        static RotationResult failed(RotationOutcome outcome) {
            return new RotationResult(outcome, null, null, null, null);
        }
    }

    @Transactional
    public IssuedToken issue(UUID tenantId, UUID staffId, UUID deviceId, UUID familyId) {
        String token = tenantId + "." + Secrets.randomSecret();
        Instant expiresAt = Instant.now().plus(properties.refreshTokenTtl());

        tokens.save(RefreshToken.issue(Ids.newId(), tenantId, familyId, staffId,
                deviceId, Secrets.sha256(token), expiresAt));

        return new IssuedToken(token, familyId);
    }

    @Transactional
    public RotationResult rotate(UUID tenantId, String presentedToken) {
        Optional<RefreshToken> found = tokens.findByTokenHash(Secrets.sha256(presentedToken));
        if (found.isEmpty()) {
            return RotationResult.failed(RotationOutcome.UNKNOWN);
        }

        RefreshToken current = found.get();

        if (current.isConsumed()) {
            revokeFamily(current.familyId(), "REUSE_DETECTED");
            return RotationResult.failed(RotationOutcome.REUSED);
        }
        if (current.isRevoked()) {
            return RotationResult.failed(RotationOutcome.REVOKED);
        }
        if (current.isExpired(Instant.now())) {
            return RotationResult.failed(RotationOutcome.EXPIRED);
        }

        current.consume();

        String next = tenantId + "." + Secrets.randomSecret();
        Instant expiresAt = Instant.now().plus(properties.refreshTokenTtl());
        tokens.save(RefreshToken.issue(Ids.newId(), tenantId, current.familyId(),
                current.staffId(), current.deviceId(), Secrets.sha256(next), expiresAt));

        return new RotationResult(RotationOutcome.OK, next, current.familyId(),
                current.staffId(), current.deviceId());
    }

    @Transactional
    public void revokeFamily(UUID familyId, String reason) {
        tokens.findAllByFamilyId(familyId).forEach(token -> {
            if (!token.isRevoked()) {
                token.revoke(reason);
            }
        });
    }

    @Transactional
    public void revokeByToken(String presentedToken) {
        tokens.findByTokenHash(Secrets.sha256(presentedToken))
                .ifPresent(token -> revokeFamily(token.familyId(), "LOGOUT"));
    }
}