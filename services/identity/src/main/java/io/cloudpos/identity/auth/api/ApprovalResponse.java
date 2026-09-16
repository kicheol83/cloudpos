package io.cloudpos.identity.auth.api;

import io.cloudpos.identity.security.ApprovalTokenIssuer;

public record ApprovalResponse(String approval_token, long expires_in) {

    public static ApprovalResponse from(ApprovalTokenIssuer.Approval approval) {
        return new ApprovalResponse(approval.token(), approval.expiresInSeconds());
    }
}
