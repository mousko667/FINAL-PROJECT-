package com.oct.invoicesystem.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Test configuration that excludes audit-related entities to avoid JSONB compatibility issues with H2
 */
@Configuration
@ComponentScan(
    basePackages = "com.oct.invoicesystem",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            com.oct.invoicesystem.domain.audit.model.AuditLog.class
        }
    )
)
public class TestConfig {
}