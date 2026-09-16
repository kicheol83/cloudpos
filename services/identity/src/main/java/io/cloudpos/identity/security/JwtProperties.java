package io.cloudpos.identity.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudpos.jwt")
public record JwtProperties(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

    public JwtProperties {
        issuer = issuer == null ? "https://identity.cloudpos.io" : issuer;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
    }
}
