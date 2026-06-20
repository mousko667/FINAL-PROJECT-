package com.oct.invoicesystem.domain.audit.dto;

import java.time.LocalDate;
import java.util.List;

/** Aggregated audit summary over [from, to], grouped along four dimensions (M10 #12). */
public record AuditSummaryDTO(
        LocalDate from,
        LocalDate to,
        long totalEvents,
        List<CountEntry> byAction,
        List<CountEntry> byUser,
        List<CountEntry> byEntityType,
        List<CountEntry> byDay) {}
