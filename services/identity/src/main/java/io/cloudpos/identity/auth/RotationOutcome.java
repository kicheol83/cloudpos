package io.cloudpos.identity.auth;

public enum RotationOutcome {
    OK,
    UNKNOWN,
    REUSED,
    REVOKED,
    EXPIRED
}