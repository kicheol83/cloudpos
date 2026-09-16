package io.cloudpos.identity.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PinEncoder {

    private final Argon2PasswordEncoder encoder =
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    public String encode(String pin) {
        return encoder.encode(pin);
    }

    public boolean matches(String pin, String hash) {
        return encoder.matches(pin, hash);
    }
}
