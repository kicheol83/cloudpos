package io.cloudpos.identity.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.cloudpos.identity.auth.StaffSession;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenIssuer {

    private final TokenKeys keys;
    private final JwtProperties properties;

    public AccessTokenIssuer(TokenKeys keys, JwtProperties properties) {
        this.keys = keys;
        this.properties = properties;
    }

    public String issue(StaffSession session) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .subject(session.staffId().toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .claim("tid", session.tenantId().toString())
                .claim("sid", session.storeId().toString())
                .claim("did", session.deviceId().toString())
                .claim("role", session.role().name())
                .claim("name", session.displayName())
                .claim("shift", session.shiftId() == null ? null : session.shiftId().toString())
                .build();

        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keys.keyId()).build(),
                    claims);
            jwt.sign(new RSASSASigner(keys.signingKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign access token", e);
        }
    }

    public long accessTokenSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
