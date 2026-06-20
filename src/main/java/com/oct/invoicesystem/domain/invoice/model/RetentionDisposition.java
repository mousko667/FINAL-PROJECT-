package com.oct.invoicesystem.domain.invoice.model;

/** Disposition decision for a document past its retention horizon (M10 #10 refinement). */
public enum RetentionDisposition {
    /** Past horizon (or not), no decision taken yet. Default. Counted by the retention sweep. */
    PENDING,
    /** Deliberately kept (legal value / legal hold). No longer counted. */
    RETAINED,
    /** Purge / cold-archive decision recorded (marking only — no physical delete here). */
    PURGED
}
