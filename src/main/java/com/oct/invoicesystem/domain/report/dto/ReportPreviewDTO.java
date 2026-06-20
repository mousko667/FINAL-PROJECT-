package com.oct.invoicesystem.domain.report.dto;

import java.util.List;

/** In-app preview of a report definition's dataset (M11 #10), truncated to a row limit. */
public record ReportPreviewDTO(
        List<String> columns,
        List<List<String>> rows,
        int totalRows,
        String dataset,
        String format) {
}
