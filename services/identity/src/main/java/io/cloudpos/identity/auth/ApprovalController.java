package io.cloudpos.identity.auth;

import io.cloudpos.identity.auth.api.ApprovalRequest;
import io.cloudpos.identity.auth.api.ApprovalResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @PostMapping
    public ApprovalResponse approve(@Valid @RequestBody ApprovalRequest request) {
        return ApprovalResponse.from(approvals.approve(
                request.approver_staff_id(), request.pin(), request.action()));
    }
}
