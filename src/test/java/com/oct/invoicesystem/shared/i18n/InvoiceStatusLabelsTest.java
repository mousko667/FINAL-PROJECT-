package com.oct.invoicesystem.shared.i18n;

import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression tests for AUDIT-039: the key {@code invoice.status.en_controle_aa} was missing from
 * BOTH catalogues, so every list export failed with HTTP 500 as soon as one invoice was in that
 * state. A FR/EN parity check could not catch it — both files were incomplete the same way — hence
 * a test parameterized over the enum itself.
 */
class InvoiceStatusLabelsTest {

    private static MessageSource catalogues() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @ParameterizedTest
    @EnumSource(InvoiceStatus.class)
    @DisplayName("chaque statut a une cle i18n en FR (sans repli) - AUDIT-039")
    void everyStatusHasFrenchKey(InvoiceStatus status) {
        MessageSource source = catalogues();
        String key = InvoiceStatusLabels.keyFor(status.name());

        assertThatCode(() -> source.getMessage(key, null, Locale.FRENCH))
                .as("cle FR manquante : %s", key)
                .doesNotThrowAnyException();
        assertThat(source.getMessage(key, null, Locale.FRENCH)).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(InvoiceStatus.class)
    @DisplayName("chaque statut a une cle i18n en EN (sans repli) - AUDIT-039")
    void everyStatusHasEnglishKey(InvoiceStatus status) {
        MessageSource source = catalogues();
        String key = InvoiceStatusLabels.keyFor(status.name());

        assertThatCode(() -> source.getMessage(key, null, Locale.ENGLISH))
                .as("cle EN manquante : %s", key)
                .doesNotThrowAnyException();
        assertThat(source.getMessage(key, null, Locale.ENGLISH)).isNotBlank();
    }

    @Test
    @DisplayName("EN_CONTROLE_AA est traduit dans les deux langues - AUDIT-039")
    void enControleAaIsTranslated() {
        MessageSource source = catalogues();

        assertThat(InvoiceStatusLabels.localize(source, InvoiceStatus.EN_CONTROLE_AA, Locale.FRENCH))
                .isEqualTo("En contrôle AA");
        assertThat(InvoiceStatusLabels.localize(source, InvoiceStatus.EN_CONTROLE_AA, Locale.ENGLISH))
                .isEqualTo("Under AA review");
    }

    @Test
    @DisplayName("une cle absente degrade en libelle brut au lieu de lever - AUDIT-039")
    void missingKeyDegradesInsteadOfThrowing() {
        // An empty catalogue simulates a status added to the code without its i18n key: the raw
        // MessageSource throws, the helper must not.
        ReloadableResourceBundleMessageSource empty = new ReloadableResourceBundleMessageSource();
        empty.setBasename("classpath:i18n/does-not-exist");

        assertThatCode(() -> empty.getMessage(
                InvoiceStatusLabels.keyFor(InvoiceStatus.EN_CONTROLE_AA.name()), null, Locale.FRENCH))
                .isInstanceOf(NoSuchMessageException.class);

        assertThat(InvoiceStatusLabels.localize(empty, InvoiceStatus.EN_CONTROLE_AA, Locale.FRENCH))
                .isEqualTo(InvoiceStatus.EN_CONTROLE_AA.getFrenchLabel());
        assertThat(InvoiceStatusLabels.localize(empty, InvoiceStatus.EN_CONTROLE_AA, Locale.ENGLISH))
                .isEqualTo(InvoiceStatus.EN_CONTROLE_AA.getEnglishLabel());
    }

    @Test
    @DisplayName("la variante String couvre l'historique de workflow - AUDIT-039")
    void rawStringVariantResolvesHistoryStatuses() {
        MessageSource source = catalogues();

        assertThat(InvoiceStatusLabels.localize(source, "EN_CONTROLE_AA", Locale.FRENCH))
                .isEqualTo("En contrôle AA");
        // Unknown or null values degrade rather than fail the whole export.
        assertThat(InvoiceStatusLabels.localize(source, "STATUT_INCONNU", Locale.FRENCH))
                .isEqualTo("STATUT_INCONNU");
        assertThat(InvoiceStatusLabels.localize(source, (String) null, Locale.FRENCH)).isEmpty();
        assertThat(InvoiceStatusLabels.localize(source, (InvoiceStatus) null, Locale.FRENCH)).isEmpty();
    }
}
