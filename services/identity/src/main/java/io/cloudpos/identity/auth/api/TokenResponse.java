package io.cloudpos.identity.auth.api;

import io.cloudpos.identity.auth.AuthHandler;
import java.util.UUID;

public record TokenResponse(String access_token, String refresh_token, String token_type,
                            long expires_in, UUID staff_id, UUID store_id, UUID shift_id,
                            String display_name, String role) {

    public static TokenResponse from(AuthHandler.Tokens tokens) {
        return new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                "Bearer",
                tokens.expiresInSeconds(),
                tokens.session().staffId(),
                tokens.session().storeId(),
                tokens.session().shiftId(),
                tokens.session().displayName(),
                tokens.session().role().name());
    }
}
