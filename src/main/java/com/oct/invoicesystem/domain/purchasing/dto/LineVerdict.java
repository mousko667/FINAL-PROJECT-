package com.oct.invoicesystem.domain.purchasing.dto;

/** Verdict de rapprochement d'une ligne facture vs PO. */
public enum LineVerdict { MATCHED, WITHIN_TOLERANCE, MISMATCH, MISSING_IN_PO }
