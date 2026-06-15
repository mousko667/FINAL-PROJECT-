package com.oct.invoicesystem.shared.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Year;

@Component
public class ReferenceNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    public ReferenceNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextReferenceNumber() {
        int year = Year.now().getValue();

        // The sequence name is built from the current calendar year only — never from user input.
        // We still hard-validate it as a 4-digit number so the identifier interpolated into the
        // (unavoidable) dynamic DDL can never be anything but digits: removes any SQL-injection
        // surface even if this method is later refactored to take an external value.
        if (year < 1000 || year > 9999) {
            throw new IllegalStateException("Unexpected year for reference sequence: " + year);
        }
        String sequenceName = "invoice_ref_seq_" + year; // e.g. invoice_ref_seq_2026 — digits only

        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS " + sequenceName + " START WITH 1 INCREMENT BY 1");
        Long value = jdbcTemplate.queryForObject("SELECT nextval('" + sequenceName + "')", Long.class);
        long sequenceValue = value == null ? 1L : value;
        return String.format("FAC-%d-%05d", year, sequenceValue);
    }
}
