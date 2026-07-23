package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a three-way matching result in its own transaction (AUDIT-034).
 *
 * <p>A MISMATCH blocks the submission by raising a {@code WorkflowException}, and that exception
 * rolled the caller's transaction back — taking the freshly saved result with it. Only successful
 * matchings left a trace, so the audit trail lost precisely the cases that need justifying, and
 * {@code three_way_matching_results} sat at 0 rows despite repeated MISMATCH runs.</p>
 *
 * <p>This is a separate bean on purpose: {@code REQUIRES_NEW} on a private method of
 * {@link ThreeWayMatchingService} would be self-invocation and the proxy would never apply it.
 * {@code ThreeWayMatchingResult} stays append-only (CLAUDE.md §9) — this class only inserts.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingResultRecorder {

    private final ThreeWayMatchingResultRepository matchingResultRepository;

    /**
     * Records a matching result so it survives a rollback of the calling transaction.
     *
     * @param result the result to persist
     * @return the persisted result
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ThreeWayMatchingResult record(ThreeWayMatchingResult result) {
        return matchingResultRepository.save(result);
    }
}
