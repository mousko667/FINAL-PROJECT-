package com.oct.invoicesystem.shared.export;

import com.oct.invoicesystem.domain.user.model.User;
import org.springframework.context.MessageSource;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable metadata block stamped on generated PDF reports: who generated it (name + role),
 * when, and — when applicable — the covered period. Built in the service layer from the caller's
 * {@code Authentication}; {@code periodLabel} is null for reports that have no date range.
 */
public record ReportMetadata(
        String generatorName,
        String generatorRole,
        Instant generatedAt,
        String periodLabel
) {

    /**
     * Resolves the report generator from the current {@link User}: "LASTNAME Firstname" plus the
     * localized role label (report.pdf.role.&lt;ROLE&gt;, DAF prioritized over ASSISTANT_COMPTABLE,
     * then the first role found, falling back to the raw role code). {@code periodLabel} is passed
     * through unchanged (null for reports that have no date range).
     */
    public static ReportMetadata of(User user, MessageSource messageSource, String periodLabel, Locale locale) {
        String name = ((user.getLastName() == null ? "" : user.getLastName()) + " "
                + (user.getFirstName() == null ? "" : user.getFirstName())).trim();
        String roleCode = resolveRoleCode(user);
        String roleLabel = roleCode == null ? ""
                : messageSource.getMessage("report.pdf.role." + roleCode, null, roleCode, locale);
        return new ReportMetadata(name, roleLabel, Instant.now(), periodLabel);
    }

    /** DAF first, then ASSISTANT_COMPTABLE, else the first role name, else null. */
    private static String resolveRoleCode(User user) {
        Set<String> names = user.getUserRoles() == null ? Set.of()
                : user.getUserRoles().stream()
                    .map(ur -> ur.getRole() == null ? null : ur.getRole().getName())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        if (names.contains("ROLE_DAF")) return "ROLE_DAF";
        if (names.contains("ROLE_ASSISTANT_COMPTABLE")) return "ROLE_ASSISTANT_COMPTABLE";
        return names.stream().findFirst().orElse(null);
    }
}
