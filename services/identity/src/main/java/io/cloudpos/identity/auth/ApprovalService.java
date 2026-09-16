package io.cloudpos.identity.auth;

import io.cloudpos.identity.security.ApprovalTokenIssuer;
import io.cloudpos.identity.staff.PinVerification;
import io.cloudpos.identity.staff.Staff;
import io.cloudpos.identity.staff.StaffRole;
import io.cloudpos.identity.staff.StaffService;
import io.cloudpos.security.AuthContext;
import io.cloudpos.security.StaffPrincipal;
import io.cloudpos.web.ApiException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ApprovalService {

    private static final Set<StaffRole> APPROVERS = Set.of(StaffRole.OWNER, StaffRole.MANAGER);

    private final StaffService staff;
    private final ApprovalTokenIssuer tokens;

    public ApprovalService(StaffService staff, ApprovalTokenIssuer tokens) {
        this.staff = staff;
        this.tokens = tokens;
    }

    public ApprovalTokenIssuer.Approval approve(UUID approverId, String pin, String action) {
        StaffPrincipal requester = AuthContext.require();

        Staff approver = staff.get(approverId);
        if (!APPROVERS.contains(approver.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "APPROVER_ROLE_INSUFFICIENT",
                    "This staff member cannot approve privileged actions");
        }

        PinVerification verification = staff.verifyPin(approverId, requester.storeId(), pin);
        switch (verification) {
            case OK -> { }
            case LOCKED -> throw new ApiException(HttpStatus.LOCKED, "PIN_LOCKED",
                    "Too many failed attempts. Try again later.");
            case NOT_EMPLOYED -> throw new ApiException(HttpStatus.FORBIDDEN,
                    "STAFF_NOT_EMPLOYED", "This staff member is not active");
            case WRONG_STORE -> throw new ApiException(HttpStatus.FORBIDDEN,
                    "STAFF_WRONG_STORE", "This staff member does not belong to this store");
            case WRONG_PIN -> throw new ApiException(HttpStatus.UNAUTHORIZED, "PIN_INVALID",
                    "Incorrect PIN");
        }

        return tokens.issue(requester.tenantId(), requester.storeId(), approverId,
                approver.role().name(), action);
    }
}
