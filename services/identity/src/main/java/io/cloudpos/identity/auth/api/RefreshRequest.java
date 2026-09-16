package io.cloudpos.identity.auth.api;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refresh_token) {
}
