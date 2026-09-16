package io.cloudpos.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudpos.security")
public record ServiceSecurityProperties(List<String> publicPaths) {

    public ServiceSecurityProperties {
        publicPaths = publicPaths == null ? List.of() : publicPaths;
    }
}
