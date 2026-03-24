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
        String sequenceName = "invoice_ref_seq_" + year;
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS " + sequenceName + " START WITH 1 INCREMENT BY 1");
        Long value = jdbcTemplate.queryForObject("SELECT nextval('" + sequenceName + "')", Long.class);
        long sequenceValue = value == null ? 1L : value;
        return String.format("FAC-%d-%05d", year, sequenceValue);
    }
}
