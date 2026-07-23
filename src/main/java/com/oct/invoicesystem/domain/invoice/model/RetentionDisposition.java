package com.oct.invoicesystem.domain.invoice.model;

/** Disposition decision for a document past its retention horizon (M10 #10 refinement). */
public enum RetentionDisposition {
    /** Past horizon (or not), no decision taken yet. Default. Counted by the retention sweep. */
    PENDING,
    /** Deliberately kept (legal value / legal hold). No longer counted. */
    RETAINED,
    /**
     * Purge proposed by the ADMIN, awaiting DAF confirmation (AUDIT-009 / decision D5).
     * Destroying a financial supporting document may not rest on the technical administrator
     * alone, so PURGED is reachable only through this intermediate state, confirmed by the DAF.
     */
    PURGE_PROPOSED,
    /** Purge / cold-archive decision recorded (marking only — no physical delete here). */
    PURGED
}
