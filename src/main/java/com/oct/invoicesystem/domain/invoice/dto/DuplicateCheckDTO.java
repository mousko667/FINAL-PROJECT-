package com.oct.invoicesystem.domain.invoice.dto;

/**
 * Result of a non-blocking duplicate pre-check performed while an invoice is being entered.
 *
 * <p>Unlike the blocking check enforced at submission time, this is purely advisory: the UI uses
 * it to warn the user that a similar invoice may already exist before they submit.</p>
 *
 * @param duplicate     {@code true} when at least one matching non-rejected, non-archived invoice
 *                      already exists for the same supplier and description in the look-back window
 * @param count         number of matching existing invoices found
 */
public record DuplicateCheckDTO(
        boolean duplicate,
        long count
) {
}
