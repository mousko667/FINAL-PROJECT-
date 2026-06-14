package com.oct.invoicesystem.domain.user.dto;

import java.util.List;

/**
 * Outcome of a bulk CSV user import (P11-16). Imports are create-only: each data row either
 * creates a new user or is rejected and reported in {@link #errors} (1-based line numbers
 * matching the file, header = line 1). Valid rows are still imported when others fail.
 */
public record UserImportResultDTO(
        int totalRows,
        int created,
        int failed,
        List<RowError> errors
) {
    public record RowError(int line, String username, String message) {}
}
