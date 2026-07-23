package com.oct.invoicesystem.shared.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Turns a client-supplied {@code sort=field,direction} parameter into a {@link Sort}, accepting only
 * fields on an explicit allow-list (AUDIT-010).
 *
 * <p>The three list endpoints used to hand {@code sort.split(",")[0]} straight to {@code Sort.by}.
 * Spring Data validates the property against the metamodel, so there was no SQL injection — but an
 * unknown property produced an HTTP <strong>500</strong>, and this was reachable from the supplier
 * portal: {@code GET /api/v1/supplier/invoices?sort=zzz} with a SUPPLIER token returned 500. A
 * relation path such as {@code department.nameFr} also worked, pulling in unintended joins.</p>
 *
 * <p>An unknown field falls back silently to the endpoint's default rather than failing: sorting is
 * a presentation concern, and refusing the whole request would be a harsher answer than the caller's
 * mistake warrants. The empty-string case needs no special handling — runtime measurement showed
 * {@code ?sort=} already returns 200 (the P1 hypothesis to the contrary was disproved in P3).</p>
 */
@Slf4j
public final class SortWhitelist {

    private SortWhitelist() {
    }

    /**
     * Resolves a sort parameter against an allow-list.
     *
     * @param sort         raw {@code field,direction} parameter, may be null or blank
     * @param allowed      sortable field names for this endpoint
     * @param defaultField field to fall back on when the requested one is not allowed
     * @return a safe {@link Sort}, never null
     */
    public static Sort resolve(String sort, Set<String> allowed, String defaultField) {
        String[] parts = sort == null ? new String[0] : sort.split(",");
        String requested = parts.length > 0 ? parts[0].trim() : "";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        if (!allowed.contains(requested)) {
            if (!requested.isEmpty()) {
                log.debug("Ignoring non-sortable field '{}', falling back to '{}'", requested, defaultField);
            }
            return Sort.by(direction, defaultField);
        }
        return Sort.by(direction, requested);
    }
}
