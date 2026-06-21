package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.purchasing.dto.LineVerdict;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingComparatorTest {

    private final MatchingComparator comparator = new MatchingComparator();

    private MatchingConfig config(String pct, String amount) {
        return MatchingConfig.builder()
                .tolerancePercentage(new BigDecimal(pct))
                .toleranceAmount(new BigDecimal(amount))
                .build();
    }

    @Test
    void exactMatch_isMatched() {
        LineVerdict v = comparator.verdictForLine(
                new BigDecimal("10"), new BigDecimal("5.00"),
                new BigDecimal("10"), new BigDecimal("5.00"), config("2.00", "0"));
        assertThat(v).isEqualTo(LineVerdict.MATCHED);
    }

    @Test
    void smallDiffWithinTolerance_isWithinTolerance() {
        // qté 10 vs 10.1 → 1% < 2% → within
        LineVerdict v = comparator.verdictForLine(
                new BigDecimal("10.1"), new BigDecimal("5.00"),
                new BigDecimal("10"), new BigDecimal("5.00"), config("2.00", "0"));
        assertThat(v).isEqualTo(LineVerdict.WITHIN_TOLERANCE);
    }

    @Test
    void largeDiffOutsideTolerance_isMismatch() {
        LineVerdict v = comparator.verdictForLine(
                new BigDecimal("20"), new BigDecimal("5.00"),
                new BigDecimal("10"), new BigDecimal("5.00"), config("2.00", "0"));
        assertThat(v).isEqualTo(LineVerdict.MISMATCH);
    }
}
