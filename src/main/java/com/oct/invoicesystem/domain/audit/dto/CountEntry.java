package com.oct.invoicesystem.domain.audit.dto;

/** One aggregated bucket of the audit summary report: a label and its event count. */
public record CountEntry(String label, long count) {}
