package com.oct.invoicesystem.shared.i18n;

import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import org.springframework.context.MessageSource;

import java.util.Locale;

/**
 * Resolves the localized label of an {@link InvoiceStatus} for exports and reports.
 *
 * <p>Audit finding AUDIT-039: the label key is built dynamically from the enum name
 * ({@code "invoice.status." + name().toLowerCase()}). When a new status was added to the code
 * without adding its key to both catalogues, {@code MessageSource#getMessage(code, args, locale)}
 * threw {@code NoSuchMessageException} and every list export failed with HTTP 500 — a single
 * invoice in that state was enough to break the whole export.</p>
 *
 * <p>Every call here supplies a default message, so a missing key degrades to a readable raw
 * label instead of failing the request. The parity of the catalogues is enforced separately by
 * a parameterized test covering all {@link InvoiceStatus} values.</p>
 */
public final class InvoiceStatusLabels {

    private InvoiceStatusLabels() {
    }

    /**
     * Returns the localized label of {@code status}, falling back to the enum's own French or
     * English label when the i18n key is absent from the catalogue.
     *
     * @param messageSource the catalogue to read from
     * @param status        the workflow status to translate; {@code null} yields an empty string
     * @param locale        the requested locale
     * @return the localized label, never {@code null}
     */
    public static String localize(MessageSource messageSource, InvoiceStatus status, Locale locale) {
        if (status == null) {
            return "";
        }
        return messageSource.getMessage(keyFor(status.name()), null, defaultLabel(status, locale), locale);
    }

    /**
     * Variant for call sites that only hold the status as a raw string (workflow history rows
     * store {@code fromStatus}/{@code toStatus} as text). An unparseable value degrades to the
     * raw string rather than failing.
     *
     * @param messageSource the catalogue to read from
     * @param statusName    the raw enum name; {@code null} yields an empty string
     * @param locale        the requested locale
     * @return the localized label, never {@code null}
     */
    public static String localize(MessageSource messageSource, String statusName, Locale locale) {
        if (statusName == null) {
            return "";
        }
        String fallback = statusName;
        try {
            fallback = defaultLabel(InvoiceStatus.valueOf(statusName), locale);
        } catch (IllegalArgumentException ignored) {
            // Unknown status name: keep the raw value as the fallback label.
        }
        return messageSource.getMessage(keyFor(statusName), null, fallback, locale);
    }

    /**
     * Builds the catalogue key of a status name. Exposed so the parity test can assert that every
     * {@link InvoiceStatus} has a key in both catalogues without duplicating the naming rule.
     *
     * @param statusName the raw enum name
     * @return the {@code invoice.status.*} catalogue key
     */
    public static String keyFor(String statusName) {
        return "invoice.status." + statusName.toLowerCase(Locale.ROOT);
    }

    private static String defaultLabel(InvoiceStatus status, Locale locale) {
        return Locale.ENGLISH.getLanguage().equals(locale == null ? null : locale.getLanguage())
                ? status.getEnglishLabel()
                : status.getFrenchLabel();
    }
}
