package com.oct.invoicesystem.domain.purchasing.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUDIT-034 — a matching result must survive the rollback of the transaction that rejects it.
 *
 * <p>Runtime evidence: {@code select count(*) from three_way_matching_results} stayed at
 * <strong>0</strong> after four submissions that each produced a MISMATCH, while a successful
 * matching in the same session did land a row. The {@code WorkflowException} raised to block the
 * submission rolled the caller back — and the {@code save} that preceded it with them. So exactly
 * the cases an auditor needs to justify were the ones leaving no trace.</p>
 *
 * <p>The fix hinges on two structural properties that are easy to lose in a later refactor, so both
 * are asserted here rather than described in a comment:</p>
 * <ol>
 *   <li>the persistence carries {@code REQUIRES_NEW} — a plain {@code @Transactional} would simply
 *       join the doomed transaction and change nothing;</li>
 *   <li>it lives on a <strong>separate bean</strong>. Spring's proxy does not intercept
 *       self-invocation, so the same annotation on a private method of {@link ThreeWayMatchingService}
 *       would be silently inert — the failure mode this test exists to prevent.</li>
 * </ol>
 */
class MatchingResultRecorderTest {

    @Test
    void record_isAnnotatedRequiresNew_soAMismatchRollbackCannotEraseTheTrail() throws Exception {
        Method record = MatchingResultRecorder.class.getMethod(
                "record", com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult.class);

        Transactional tx = record.getAnnotation(Transactional.class);
        assertThat(tx)
                .as("record() must be transactional, otherwise it joins the caller's doomed transaction")
                .isNotNull();
        assertThat(tx.propagation())
                .as("REQUIRES_NEW is the whole point: the trail must outlive the caller's rollback")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void recorder_isASeparateBean_soTheProxyActuallyApplies() {
        // Self-invocation bypasses the Spring proxy: had the annotation stayed on a private method
        // of ThreeWayMatchingService, it would never have taken effect and the finding would look
        // fixed while the table stayed empty.
        assertThat(MatchingResultRecorder.class)
                .as("the recorder must not be folded back into ThreeWayMatchingService")
                .isNotEqualTo(ThreeWayMatchingService.class);
        assertThat(MatchingResultRecorder.class.getAnnotation(org.springframework.stereotype.Component.class))
                .as("it must be a Spring bean, so the transactional proxy wraps it")
                .isNotNull();
    }
}
