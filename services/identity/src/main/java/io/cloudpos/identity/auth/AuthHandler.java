package io.cloudpos.identity.auth;

import io.cloudpos.identity.security.AccessTokenIssuer;
import io.cloudpos.identity.staff.Staff;
import io.cloudpos.identity.staff.StaffService;
import io.cloudpos.tenancy.TenantContext;
import io.cloudpos.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthHandler {

    private final DeviceDirectoryRepository directory;
    private final StaffAuthService staffAuth;
    private final StaffService staff;
    private final RefreshTokenService refreshTokens;
    private final AccessTokenIssuer accessTokens;

    public AuthHandler(DeviceDirectoryRepository directory, StaffAuthService staffAuth,
                       StaffService staff, RefreshTokenService refreshTokens,
                       AccessTokenIssuer accessTokens) {
        this.directory = directory;
        this.staffAuth = staffAuth;
        this.staff = staff;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
    }

    public record Tokens(String accessToken, String refreshToken, long expiresInSeconds,
                         StaffSession session) {
    }

    public Tokens login(UUID deviceId, String deviceSecret, UUID staffId, String pin) {
        DeviceDirectory entry = directory.findById(deviceId).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "DEVICE_CREDENTIAL_INVALID",
                "Device credential rejected"));

        TenantContext.set(entry.tenantId());

        StaffSession session = staffAuth.authenticate(deviceId, deviceSecret, staffId, pin);

        RefreshTokenService.IssuedToken refresh = refreshTokens.issue(
                entry.tenantId(), session.staffId(), session.deviceId(), UUID.randomUUID());

        return new Tokens(accessTokens.issue(session), refresh.token(),
                accessTokens.accessTokenSeconds(), session);
    }

    public Tokens refresh(String refreshToken) {
        UUID tenantId = tenantOf(refreshToken);
        TenantContext.set(tenantId);

        RefreshTokenService.RotationResult rotated = refreshTokens.rotate(tenantId, refreshToken);
        switch (rotated.outcome()) {
            case OK -> { }
            case UNKNOWN -> throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_INVALID", "Refresh token not recognised");
            case REUSED -> throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_REUSED",
                    "Refresh token was already used. All sessions have been revoked.");
            case REVOKED -> throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_REVOKED", "Refresh token has been revoked");
            case EXPIRED -> throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }

        Staff member = staff.get(rotated.staffId());

        DeviceDirectory entry = directory.findById(rotated.deviceId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "DEVICE_CREDENTIAL_INVALID", "Device is no longer registered"));

        StaffSession session = new StaffSession(tenantId, entry.storeId(), member.id(),
                entry.deviceId(), member.displayName(), member.role());

        return new Tokens(accessTokens.issue(session), rotated.token(),
                accessTokens.accessTokenSeconds(), session);
    }

    public void logout(String refreshToken) {
        TenantContext.set(tenantOf(refreshToken));
        refreshTokens.revokeByToken(refreshToken);
    }

    private UUID tenantOf(String refreshToken) {
        int separator = refreshToken.indexOf('.');
        if (separator <= 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID",
                    "Refresh token not recognised");
        }
        try {
            return UUID.fromString(refreshToken.substring(0, separator));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID",
                    "Refresh token not recognised");
        }
    }
}