package io.cloudpos.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TokenKeys {

    private final RSAKey key;

    public TokenKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate signing key", e);
        }
    }

    public RSAKey signingKey() {
        return key;
    }

    public String keyId() {
        return key.getKeyID();
    }

    public Map<String, Object> publicJwks() {
        return new JWKSet(key.toPublicJWK()).toJSONObject();
    }
}
