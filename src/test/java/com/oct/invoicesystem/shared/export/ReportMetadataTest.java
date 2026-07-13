package com.oct.invoicesystem.shared.export;

import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportMetadataTest {

    @Test
    void holdsAllFields_andAllowsNullPeriod() {
        Instant now = Instant.now();
        ReportMetadata meta = new ReportMetadata("DUPONT Jean", "DAF (Directeur Administratif et Financier)", now, null, null);

        assertEquals("DUPONT Jean", meta.generatorName());
        assertEquals("DAF (Directeur Administratif et Financier)", meta.generatorRole());
        assertEquals(now, meta.generatedAt());
        assertNull(meta.periodLabel());
    }

    @Test
    void keepsPeriodLabelWhenProvided() {
        ReportMetadata meta = new ReportMetadata("NOM Prenom", "Assistant comptable", Instant.now(),
                "Periode du 2026-01-01 au 2026-01-31", null);
        assertEquals("Periode du 2026-01-01 au 2026-01-31", meta.periodLabel());
    }

    @Test
    void of_ordersNameAsLastnameFirstname_andPrioritizesDafRole() {
        User user = userWith("Jean", "Dupont", "ROLE_ASSISTANT_COMPTABLE", "ROLE_DAF");
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(eq("report.pdf.role.ROLE_DAF"), any(), eq("ROLE_DAF"), eq(Locale.FRENCH)))
                .thenReturn("DAF (Directeur Administratif et Financier)");

        ReportMetadata meta = ReportMetadata.of(user, messageSource, null, Locale.FRENCH);

        assertEquals("Dupont Jean", meta.generatorName());
        assertEquals("DAF (Directeur Administratif et Financier)", meta.generatorRole());
        assertNull(meta.periodLabel());
    }

    @Test
    void of_passesPeriodLabelThrough() {
        User user = userWith("Marie", "Nguema", "ROLE_ASSISTANT_COMPTABLE");
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(eq("report.pdf.role.ROLE_ASSISTANT_COMPTABLE"), any(),
                eq("ROLE_ASSISTANT_COMPTABLE"), eq(Locale.FRENCH))).thenReturn("Assistant comptable");

        ReportMetadata meta = ReportMetadata.of(user, messageSource, "Periode du 2026-01-01 au 2026-01-31", Locale.FRENCH);

        assertEquals("Periode du 2026-01-01 au 2026-01-31", meta.periodLabel());
    }

    private static User userWith(String firstName, String lastName, String... roleNames) {
        User user = User.builder().firstName(firstName).lastName(lastName).build();
        Set<UserRole> roles = new java.util.HashSet<>();
        for (String roleName : roleNames) {
            Role role = Role.builder().name(roleName).build();
            roles.add(UserRole.builder().role(role).build());
        }
        user.setUserRoles(roles);
        return user;
    }

    @Test
    void ofWithFiltersLabelKeepsBothPeriodAndFilters() {
        User u = new User();
        u.setFirstName("Marie");
        u.setLastName("Dubois");
        org.springframework.context.support.StaticMessageSource ms = new org.springframework.context.support.StaticMessageSource();
        ReportMetadata meta = ReportMetadata.of(u, ms, "Periode X", "Statut: Draft", Locale.FRENCH);
        assertThat(meta.periodLabel()).isEqualTo("Periode X");
        assertThat(meta.filtersLabel()).isEqualTo("Statut: Draft");
        assertThat(meta.generatorName()).isEqualTo("Dubois Marie");
    }

    @Test
    void legacyOfHasNullFiltersLabel() {
        User u = new User();
        u.setFirstName("Marie");
        u.setLastName("Dubois");
        org.springframework.context.support.StaticMessageSource ms = new org.springframework.context.support.StaticMessageSource();
        ReportMetadata meta = ReportMetadata.of(u, ms, "Periode X", Locale.FRENCH);
        assertThat(meta.filtersLabel()).isNull();
    }
}
