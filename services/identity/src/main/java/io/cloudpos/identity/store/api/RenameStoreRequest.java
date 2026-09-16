package io.cloudpos.identity.store.api;

import jakarta.validation.constraints.NotBlank;

public record RenameStoreRequest(@NotBlank String name) {
}
