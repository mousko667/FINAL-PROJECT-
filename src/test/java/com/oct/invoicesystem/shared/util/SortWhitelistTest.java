package com.oct.invoicesystem.shared.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** AUDIT-010 — an unknown sort field must degrade to the default, never blow up the request. */
class SortWhitelistTest {

    private static final Set<String> ALLOWED = Set.of("createdAt", "amount", "status");

    @Test
    void unknownField_fallsBackToTheDefault() {
        // The exact shape of the runtime evidence: ?sort=nimportequoi produced an HTTP 500.
        Sort sort = SortWhitelist.resolve("nimportequoi", ALLOWED, "createdAt");

        assertThat(sort.getOrderFor("nimportequoi")).isNull();
        assertThat(sort.getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void relationPath_isRejected() {
        // ?sort=department.nameFr returned 200 but pulled in unintended joins.
        Sort sort = SortWhitelist.resolve("department.nameFr", ALLOWED, "createdAt");

        assertThat(sort.getOrderFor("department.nameFr")).isNull();
        assertThat(sort.getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void allowedField_isHonoured() {
        // Counter-proof: the whitelist must not flatten every request onto the default.
        Sort sort = SortWhitelist.resolve("amount,desc", ALLOWED, "createdAt");

        assertThat(sort.getOrderFor("amount")).isNotNull();
        assertThat(sort.getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void directionDefaultsToAscendingAndIsCaseInsensitive() {
        assertThat(SortWhitelist.resolve("amount", ALLOWED, "createdAt")
                .getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(SortWhitelist.resolve("amount,DESC", ALLOWED, "createdAt")
                .getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void emptyAndNullSort_useTheDefault() {
        // P3 measured that ?sort= already returned 200, so this is non-regression, not a new fix.
        assertThat(SortWhitelist.resolve("", ALLOWED, "createdAt").getOrderFor("createdAt")).isNotNull();
        assertThat(SortWhitelist.resolve(null, ALLOWED, "createdAt").getOrderFor("createdAt")).isNotNull();
    }
}
