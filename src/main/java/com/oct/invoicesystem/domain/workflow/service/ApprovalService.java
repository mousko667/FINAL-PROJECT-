package com.oct.invoicesystem.domain.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ApprovalService {
    void assignReviewer(UUID invoiceId);
    void validateN1(UUID invoiceId, String comment);
    void validateN2(UUID invoiceId, String comment);
    void bonAPayer(UUID invoiceId, String comment);
    void reject(UUID invoiceId, String rejectionReason);
    List<Map<String, Object>> getApprovalSteps(UUID invoiceId);
}
