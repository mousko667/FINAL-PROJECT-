package com.oct.invoicesystem.domain.workflow.service;

import java.util.UUID;

public interface ApprovalService {
    void assignReviewer(UUID invoiceId);
    void validateN1(UUID invoiceId, String comment);
    void validateN2(UUID invoiceId, String comment);
    void bonAPayer(UUID invoiceId, String comment);
    void reject(UUID invoiceId, String rejectionReason);
}
