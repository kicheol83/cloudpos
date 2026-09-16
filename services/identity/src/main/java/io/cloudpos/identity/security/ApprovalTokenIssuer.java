package io.cloudpos.identity.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ApprovalTokenIssuer {

    private static final Duration VALIDITY = Duration.ofMinutes(2);

    private final TokenKeys keys;
    private final JwtProperties properties;

    public ApprovalTokenIssuer(TokenKeys keys, JwtProperties properties) {
        this.keys = keys;
        this.properties = properties;
    }

    public record Approval(String token, long expiresInSeconds) {
    }

    public Approval issue(UUID tenantId, UUID storeId, UUID approverId, String role,
                          String action) {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .subject(approverId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(VALIDITY)))
                .claim("tid", tenantId.toString())
                .claim("sid", storeId.toString())
                .claim("role", role)
                .claim("act", action)
                .claim("typ", "approval")
                .build();

        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keys.keyId()).build(),
                    claims);
            jwt.sign(new RSASSASigner(keys.signingKey()));
            return new Approval(jwt.serialize(), VALIDITY.toSeconds());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign approval token", e);
        }
    }
}
