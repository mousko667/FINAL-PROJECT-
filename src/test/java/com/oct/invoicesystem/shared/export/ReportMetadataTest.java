package com.oct.invoicesystem.shared.export;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReportMetadataTest {

    @Test
    void holdsAllFields_andAllowsNullPeriod() {
        Instant now = Instant.now();
        ReportMetadata meta = new ReportMetadata("DUPONT Jean", "DAF (Directeur Administratif et Financier)", now, null);

        assertEquals("DUPONT Jean", meta.generatorName());
        assertEquals("DAF (Directeur Administratif et Financier)", meta.generatorRole());
        assertEquals(now, meta.generatedAt());
        assertNull(meta.periodLabel());
    }

    @Test
    void keepsPeriodLabelWhenProvided() {
        ReportMetadata meta = new ReportMetadata("NOM Prenom", "Assistant comptable", Instant.now(),
                "Periode du 2026-01-01 au 2026-01-31");
        assertEquals("Periode du 2026-01-01 au 2026-01-31", meta.periodLabel());
    }
}
