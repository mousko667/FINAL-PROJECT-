# Reports PDF Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add generator (last name + first name + role), generation date, a dedicated signature box, the concerned period, and the official OCT logo to the project's PDF reports only.

**Architecture:** Introduce two shared helpers — a `ReportMetadata` record (immutable carrier) and a `PdfMetadata` renderer (header block + signature box) — in `shared/export`. Make `TabularExportService.toPdf` accept an optional `ReportMetadata` (null = current behaviour, so Excel/CSV and other PDF callers are unaffected). Thread `Authentication` from the report controllers down to the service methods so the generator can be resolved via the existing `SecurityHelper`. Replace the classpath logo resource file.

**Tech Stack:** Java 21, Spring Boot 3.4, iText 8 (PDF), JUnit 5 + Mockito + MockMvc, i18n via `MessageSource` (`messages_fr.properties` = ISO-8859-1 with `\uXXXX` escapes, `messages_en.properties` = UTF-8).

## Global Constraints

- PDF ONLY. Do NOT modify Excel/CSV exports, do NOT touch export columns (harmonized in commit `cc3ba7f`).
- iText 8: borders via `new SolidBorder(color, width)` — NEVER `setBorderColor` (CLAUDE.md §13).
- `messages_fr.properties` is ISO-8859-1 and uses `\uXXXX` escapes for accents — write new FR keys in pure-ASCII `\uXXXX`, never raw accented bytes. `messages_en.properties` is UTF-8. Keep FR/EN symmetric.
- Keep `@PreAuthorize("hasAnyRole('DAF','ASSISTANT_COMPTABLE')")` on every affected endpoint. ADMIN stays excluded from financial data.
- Generator name order = **last name then first name**: `user.getLastName() + " " + user.getFirstName()`.
- No Flyway migration (no schema change).
- Test baseline: `./mvnw test` = 567/0/0. A task is done only at 0 failures.
- One logical topic per commit. No push/merge to `main` without the user's explicit go-ahead.
- Working directory for all commands: `c:/Users/Dany/Documents/FINAL PROJECT/invoice-system`. Branch: `feat/reports-pdf-metadata`.

---

## File Structure

- `src/main/java/.../shared/export/ReportMetadata.java` — NEW. Immutable record carrying generator name, resolved role label, generation instant, optional period label.
- `src/main/java/.../shared/export/PdfMetadata.java` — NEW. Static renderers: header block + signature box, reused by all PDF generators.
- `src/main/java/.../shared/export/TabularExportService.java` — MODIFY. Add an `export(...)` overload + `toPdf` overload taking an optional `ReportMetadata`.
- `src/main/resources/i18n/messages_fr.properties` / `messages_en.properties` — MODIFY. Add 5 new keys each.
- `src/main/resources/branding/oct-logo.png` — REPLACE with `docs/Logo.png`.
- `src/main/java/.../domain/report/service/ReportService.java` — MODIFY. Add `Authentication` param to the two PDF methods.
- `src/main/java/.../domain/report/service/ReportServiceImpl.java` — MODIFY. Inject `SecurityHelper`, build `ReportMetadata`, render header + signature.
- `src/main/java/.../domain/report/service/ReportBuilderService.java` — MODIFY. Add `Authentication` to `run` + `executiveSummaryPdf`, pass `ReportMetadata` to export.
- `src/main/java/.../domain/report/controller/ReportController.java` — MODIFY. Add `Authentication` param to 4 endpoints, pass it through.
- Tests: `TabularExportServiceTest`, `ReportServiceTest`, `ReportControllerTest` — MODIFY.

---

## Task 1: Replace the OCT logo resource

**Files:**
- Replace: `src/main/resources/branding/oct-logo.png` (source = `docs/Logo.png`)
- Test: `src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java` (existing `pdf_embedsLetterheadLogo` already asserts a logo is embedded)

**Interfaces:**
- Consumes: nothing.
- Produces: the new branding asset used by `PdfBranding.addLetterhead` (unchanged code, path `/branding/oct-logo.png`, width 170pt).

- [ ] **Step 1: Copy the official logo over the classpath resource**

```bash
cp "docs/Logo.png" "src/main/resources/branding/oct-logo.png"
```

- [ ] **Step 2: Verify the replacement (md5 now matches docs/Logo.png)**

```bash
md5sum docs/Logo.png src/main/resources/branding/oct-logo.png
```
Expected: both md5 hashes identical.

- [ ] **Step 3: Run the letterhead test to confirm PDFs still embed a logo**

```bash
./mvnw -q -Dtest=TabularExportServiceTest test
```
Expected: PASS (all tests in class, incl. `pdf_embedsLetterheadLogo`).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/branding/oct-logo.png
git commit -m "feat(reports): logo officiel OCT dans les PDF (remplace oct-logo.png)"
```

---

## Task 2: `ReportMetadata` record + i18n keys

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/shared/export/ReportMetadata.java`
- Modify: `src/main/resources/i18n/messages_fr.properties`, `src/main/resources/i18n/messages_en.properties`
- Test: `src/test/java/com/oct/invoicesystem/shared/export/ReportMetadataTest.java` (NEW)

**Interfaces:**
- Consumes: nothing.
- Produces: `public record ReportMetadata(String generatorName, String generatorRole, java.time.Instant generatedAt, String periodLabel)` — used by `PdfMetadata`, `TabularExportService`, and both report services. `periodLabel` may be null.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/oct/invoicesystem/shared/export/ReportMetadataTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw -q -Dtest=ReportMetadataTest test
```
Expected: FAIL — `ReportMetadata` cannot be resolved / does not compile.

- [ ] **Step 3: Create the record**

Create `src/main/java/com/oct/invoicesystem/shared/export/ReportMetadata.java`:

```java
package com.oct.invoicesystem.shared.export;

import java.time.Instant;

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
) {}
```

- [ ] **Step 4: Add the 5 FR keys (ISO-8859-1, \uXXXX escapes)**

Append to `src/main/resources/i18n/messages_fr.properties` after the existing `report.pdf.period` line. Use this exact block (accented chars pre-escaped so the file stays ISO-8859-1-safe):

```properties
report.pdf.generated_by=Généré par : {0} ({1})
report.pdf.signature=Signature :
report.pdf.signature.date=Date :
report.pdf.role.ROLE_DAF=DAF (Directeur Administratif et Financier)
report.pdf.role.ROLE_ASSISTANT_COMPTABLE=Assistant comptable
```

To append safely without corrupting encoding, use ASCII-only content (the block above is pure ASCII thanks to `\uXXXX`), so a normal append is safe here:

```bash
printf 'report.pdf.generated_by=G\\u00E9n\\u00E9r\\u00E9 par : {0} ({1})\nreport.pdf.signature=Signature :\nreport.pdf.signature.date=Date :\nreport.pdf.role.ROLE_DAF=DAF (Directeur Administratif et Financier)\nreport.pdf.role.ROLE_ASSISTANT_COMPTABLE=Assistant comptable\n' >> src/main/resources/i18n/messages_fr.properties
```

- [ ] **Step 5: Add the 5 EN keys (UTF-8)**

Append to `src/main/resources/i18n/messages_en.properties` after `report.pdf.period`:

```properties
report.pdf.generated_by=Generated by: {0} ({1})
report.pdf.signature=Signature:
report.pdf.signature.date=Date:
report.pdf.role.ROLE_DAF=CFO (Finance Director)
report.pdf.role.ROLE_ASSISTANT_COMPTABLE=Accounting Assistant
```

- [ ] **Step 6: Verify FR/EN symmetry for the new keys**

```bash
for k in report.pdf.generated_by report.pdf.signature report.pdf.signature.date report.pdf.role.ROLE_DAF report.pdf.role.ROLE_ASSISTANT_COMPTABLE; do
  grep -c "^$k=" src/main/resources/i18n/messages_fr.properties src/main/resources/i18n/messages_en.properties
done
```
Expected: each key present exactly once in both files (all counts `1`).

- [ ] **Step 7: Verify FR file has no raw non-ASCII bytes introduced**

```bash
grep -nP '[^\x00-\x7F]' src/main/resources/i18n/messages_fr.properties | grep -i "report.pdf" || echo "OK: new report.pdf keys are pure ASCII"
```
Expected: `OK: new report.pdf keys are pure ASCII`.

- [ ] **Step 8: Run tests**

```bash
./mvnw -q -Dtest=ReportMetadataTest test
```
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/ReportMetadata.java \
        src/test/java/com/oct/invoicesystem/shared/export/ReportMetadataTest.java \
        src/main/resources/i18n/messages_fr.properties \
        src/main/resources/i18n/messages_en.properties
git commit -m "feat(reports): ReportMetadata + cles i18n metadonnees PDF (FR/EN)"
```

---

## Task 3: `PdfMetadata` renderer + parameterizable `toPdf`

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java`
- Modify: `src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java`
- Test: `src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java`

**Interfaces:**
- Consumes: `ReportMetadata` (Task 2).
- Produces:
  - `PdfMetadata.renderHeader(com.itextpdf.layout.Document doc, ReportMetadata meta, org.springframework.context.MessageSource ms, java.util.Locale loc)` — renders optional period line + generated_by + generated_at.
  - `PdfMetadata.renderSignatureBlock(com.itextpdf.layout.Document doc, org.springframework.context.MessageSource ms, java.util.Locale loc)` — renders the bordered signature box.
  - `TabularExportService.export(Format format, String title, List<String> headers, List<List<String>> rows, ReportMetadata meta)` — new 5-arg overload; the existing 4-arg `export` delegates with `meta = null`.

- [ ] **Step 1: Write the failing tests (append to TabularExportServiceTest)**

Add these two methods inside the existing `TabularExportServiceTest` class. Add imports if missing: `com.oct.invoicesystem.shared.export.ReportMetadata` is same-package (no import needed), and a stub `MessageSource`.

```java
    @Test
    void pdf_withoutMetadata_stillProducesPdf_backwardCompatible() {
        byte[] bytes = service.export(TabularExportService.Format.PDF, "Report",
                List.of("a", "b"), List.of(List.of("1", "2")), null);
        assertNotNull(bytes);
        String head = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", head);
    }

    @Test
    void pdf_withMetadata_isLargerThanWithout() {
        org.springframework.context.support.StaticMessageSource ms =
                new org.springframework.context.support.StaticMessageSource();
        java.util.Locale loc = java.util.Locale.FRENCH;
        ms.addMessage("report.pdf.generated_by", loc, "Genere par : {0} ({1})");
        ms.addMessage("report.pdf.generated_at", loc, "Genere le : {0}");
        ms.addMessage("report.pdf.signature", loc, "Signature :");
        ms.addMessage("report.pdf.signature.date", loc, "Date :");

        java.util.Locale prev = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        org.springframework.context.i18n.LocaleContextHolder.setLocale(loc);
        try {
            com.oct.invoicesystem.shared.export.ReportMetadata meta =
                    new com.oct.invoicesystem.shared.export.ReportMetadata(
                            "DUPONT Jean", "DAF", java.time.Instant.now(), null);
            byte[] withMeta = service.export(TabularExportService.Format.PDF, "Report",
                    List.of("a", "b"), List.of(List.of("1", "2")), meta, ms);
            byte[] withoutMeta = service.export(TabularExportService.Format.PDF, "Report",
                    List.of("a", "b"), List.of(List.of("1", "2")), null);
            assertNotNull(withMeta);
            assertEquals("%PDF", new String(withMeta, 0, 4, StandardCharsets.US_ASCII));
            assertTrue(withMeta.length > withoutMeta.length,
                    "PDF with metadata block should be larger than the plain one");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(prev);
        }
    }
```

> Note: this drives a 6-arg `export(Format, title, headers, rows, meta, MessageSource)` — the
> service needs the `MessageSource` to render the localized metadata. Wire it in Step 3.

- [ ] **Step 2: Run tests to verify they fail**

```bash
./mvnw -q -Dtest=TabularExportServiceTest test
```
Expected: FAIL — the 5-arg and 6-arg `export` overloads do not exist.

- [ ] **Step 3: Create `PdfMetadata`**

Create `src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java`:

```java
package com.oct.invoicesystem.shared.export;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.context.MessageSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared rendering of the OCT report metadata: a header block (optional period, generator name +
 * role, generation date) placed under the title, and a bordered signature box placed at the
 * bottom. Centralized so every PDF report (audit, compliance, executive summary, custom builder)
 * looks identical. Never fails report generation on a missing key — callers pass a resolved
 * {@link ReportMetadata}.
 */
public final class PdfMetadata {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private PdfMetadata() {}

    /** Header block under the title: optional period (centered), then generator + date (right). */
    public static void renderHeader(Document doc, ReportMetadata meta, MessageSource ms, Locale loc) {
        if (meta == null) {
            return;
        }
        if (meta.periodLabel() != null) {
            doc.add(new Paragraph(meta.periodLabel())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(12));
        }
        String generatedBy = ms.getMessage("report.pdf.generated_by",
                new Object[]{meta.generatorName(), meta.generatorRole()}, loc);
        doc.add(new Paragraph(generatedBy)
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(8));

        String generatedAt = ms.getMessage("report.pdf.generated_at",
                new Object[]{DATE_FORMAT.format(meta.generatedAt() == null ? Instant.now() : meta.generatedAt())}, loc);
        doc.add(new Paragraph(generatedAt)
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(8));
    }

    /** Bottom-of-page bordered box with a blank area to sign plus a date line. */
    public static void renderSignatureBlock(Document doc, MessageSource ms, Locale loc) {
        doc.add(new Paragraph("\n"));
        Table box = new Table(UnitValue.createPercentArray(new float[]{100})).useAllAvailableWidth();
        Cell cell = new Cell()
                .setBorder(new SolidBorder(ColorConstants.GRAY, 1f))
                .setHeight(90f)
                .add(new Paragraph(ms.getMessage("report.pdf.signature", null, loc))
                        .setBold().setFontSize(10))
                .add(new Paragraph("\n"))
                .add(new Paragraph(ms.getMessage("report.pdf.signature.date", null, loc))
                        .setFontSize(9));
        box.addCell(cell);
        doc.add(box);
    }
}
```

- [ ] **Step 4: Add the `export` overloads + parameterize `toPdf` in `TabularExportService`**

In `src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java`:

Replace the existing 4-arg `export` method (lines ~54-60) with these three methods:

```java
    public byte[] export(Format format, String title, List<String> headers, List<List<String>> rows) {
        return export(format, title, headers, rows, null, null);
    }

    /** Overload used when a caller has no MessageSource but wants no metadata (meta must be null). */
    public byte[] export(Format format, String title, List<String> headers, List<List<String>> rows, ReportMetadata meta) {
        return export(format, title, headers, rows, meta, null);
    }

    /**
     * Full export. For PDF, when {@code meta} is non-null a metadata header (generator, date,
     * optional period) and a signature box are rendered using {@code messageSource}; CSV/EXCEL
     * ignore {@code meta}. When {@code meta} is null the output is byte-for-byte the legacy layout.
     */
    public byte[] export(Format format, String title, List<String> headers, List<List<String>> rows,
                         ReportMetadata meta, org.springframework.context.MessageSource messageSource) {
        return switch (format) {
            case CSV -> toCsv(headers, rows);
            case EXCEL -> toExcel(title, headers, rows);
            case PDF -> toPdf(title, headers, rows, meta, messageSource);
        };
    }
```

Then replace the existing `toPdf(String, List, List)` (lines ~150-178) with:

```java
    private byte[] toPdf(String title, List<String> headers, List<List<String>> rows,
                         ReportMetadata meta, org.springframework.context.MessageSource messageSource) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfBranding.addLetterhead(document);
            document.add(new Paragraph(title == null ? "Export" : title).setBold().setFontSize(14));

            if (meta != null && messageSource != null) {
                PdfMetadata.renderHeader(document, meta, messageSource,
                        org.springframework.context.i18n.LocaleContextHolder.getLocale());
            }

            float[] widths = new float[headers.size()];
            for (int i = 0; i < widths.length; i++) widths[i] = 1f;
            Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();

            for (String h : headers) {
                table.addHeaderCell(new Cell().add(new Paragraph(h == null ? "" : h).setBold()));
            }
            for (List<String> row : rows) {
                for (String v : row) {
                    table.addCell(new Cell().add(new Paragraph(v == null ? "" : v)));
                }
            }

            document.add(table);

            if (meta != null && messageSource != null) {
                PdfMetadata.renderSignatureBlock(document, messageSource,
                        org.springframework.context.i18n.LocaleContextHolder.getLocale());
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ExportException("Failed to build PDF export", e);
        }
    }
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./mvnw -q -Dtest=TabularExportServiceTest test
```
Expected: PASS — all existing tests plus the two new ones.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java \
        src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java \
        src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java
git commit -m "feat(reports): PdfMetadata (en-tete + cadre signature) + toPdf parametrable"
```

---

## Task 4: Audit + Compliance PDF (ReportServiceImpl + ReportController)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/service/ReportService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/service/ReportServiceImpl.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/report/service/ReportServiceTest.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/report/controller/ReportControllerTest.java`

**Interfaces:**
- Consumes: `ReportMetadata` (Task 2), `PdfMetadata` (Task 3), existing `SecurityHelper.currentUser(Authentication)`.
- Produces:
  - `ReportService.generateInvoiceAuditPdf(UUID invoiceId, org.springframework.security.core.Authentication authentication)`
  - `ReportService.generateCompliancePdf(LocalDate startDate, LocalDate endDate, org.springframework.security.core.Authentication authentication)`
  - Private helper in `ReportServiceImpl`: `ReportMetadata buildMetadata(Authentication authentication, String periodLabelOrNull, Locale locale)` — resolves name (last+first) + role label, `generatedAt = Instant.now()`.

- [ ] **Step 1: Update the interface signatures**

In `ReportService.java`, add the import and change the two method signatures:

```java
import org.springframework.security.core.Authentication;
```
```java
    ByteArrayInputStream generateInvoiceAuditPdf(UUID invoiceId, Authentication authentication);

    ByteArrayInputStream generateCompliancePdf(LocalDate startDate, LocalDate endDate, Authentication authentication);
```

- [ ] **Step 2: Update the two failing service tests to the new signatures**

In `ReportServiceTest.java`:

Add a `@Mock` field next to the others (after line ~78):
```java
    @Mock
    private com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;
```

Add a shared test helper for a stub authenticated user near the top of the class body (after `setUp`):
```java
    private org.springframework.security.core.Authentication stubDafAuth() {
        com.oct.invoicesystem.domain.user.model.Role role =
                com.oct.invoicesystem.domain.user.model.Role.builder().name("ROLE_DAF").build();
        com.oct.invoicesystem.domain.user.model.UserRole ur =
                com.oct.invoicesystem.domain.user.model.UserRole.builder().role(role).build();
        com.oct.invoicesystem.domain.user.model.User user =
                com.oct.invoicesystem.domain.user.model.User.builder()
                        .firstName("Jean").lastName("Dupont")
                        .userRoles(new java.util.HashSet<>(java.util.Set.of(ur)))
                        .build();
        org.springframework.security.core.Authentication auth =
                org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
        org.mockito.Mockito.lenient().when(securityHelper.currentUser(auth)).thenReturn(user);
        return auth;
    }
```

> Verify `UserRole` has a `role(...)` builder field and `User` has a `userRoles(...)` builder
> setter (it does — `User.userRoles` is a `@Builder.Default Set<UserRole>`). If `UserRole`'s
> builder field differs, adjust to the actual field name found in `UserRole.java`.

Change the two PDF test bodies to pass the auth and stub the messageSource role key:
```java
    @Test
    void generateInvoiceAuditPdf_ReturnsStream() {
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .referenceNumber("FAC-2024-00001")
                .supplierName("Supplier A")
                .amount(BigDecimal.TEN)
                .currency("EUR")
                .status(InvoiceStatus.SOUMIS)
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(historyRepository.findByInvoiceIdOrderByChangedAtAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Label");

        ByteArrayInputStream result = reportService.generateInvoiceAuditPdf(invoiceId, stubDafAuth());
        assertNotNull(result);
    }

    @Test
    void generateCompliancePdf_ReturnsStream() {
        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Label");

        ByteArrayInputStream result = reportService.generateCompliancePdf(LocalDate.now(), LocalDate.now(), stubDafAuth());
        assertNotNull(result);
    }
```

- [ ] **Step 3: Run the service tests to verify they fail to compile / fail**

```bash
./mvnw -q -Dtest=ReportServiceTest test
```
Expected: FAIL — `ReportServiceImpl` still has the old 1-/2-arg signatures and no `securityHelper` field.

- [ ] **Step 4: Implement in `ReportServiceImpl`**

Add the field (after line ~76):
```java
    private final com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;
```

Add imports at the top:
```java
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.export.ReportMetadata;
import com.oct.invoicesystem.shared.export.PdfMetadata;
import org.springframework.security.core.Authentication;
```

Add the private helper (place it near the other private helpers):
```java
    /**
     * Resolve the report generator from the caller's authentication: "LASTNAME Firstname" plus the
     * localized role label (report.pdf.role.<ROLE>, DAF prioritized over ASSISTANT_COMPTABLE,
     * falling back to the raw role code). {@code periodLabelOrNull} is passed through unchanged.
     */
    private ReportMetadata buildMetadata(Authentication authentication, String periodLabelOrNull, Locale locale) {
        User u = securityHelper.currentUser(authentication);
        String name = ((u.getLastName() == null ? "" : u.getLastName()) + " "
                + (u.getFirstName() == null ? "" : u.getFirstName())).trim();
        String roleCode = resolveRoleCode(u);
        String roleLabel = roleCode == null ? ""
                : messageSource.getMessage("report.pdf.role." + roleCode, null, roleCode, locale);
        return new ReportMetadata(name, roleLabel, Instant.now(), periodLabelOrNull);
    }

    /** DAF first, then ASSISTANT_COMPTABLE, else the first role name, else null. */
    private String resolveRoleCode(User u) {
        java.util.Set<String> names = u.getUserRoles() == null ? java.util.Set.of()
                : u.getUserRoles().stream()
                    .map(ur -> ur.getRole() == null ? null : ur.getRole().getName())
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
        if (names.contains("ROLE_DAF")) return "ROLE_DAF";
        if (names.contains("ROLE_ASSISTANT_COMPTABLE")) return "ROLE_ASSISTANT_COMPTABLE";
        return names.stream().findFirst().orElse(null);
    }
```

> `messageSource.getMessage(code, args, defaultMessage, locale)` returns the `defaultMessage`
> (here the raw role code) if the key is missing — this is the fallback required by the spec.

Change `generateInvoiceAuditPdf` signature and its metadata rendering. Replace the existing
"Generated at" block (lines ~258-262) with a call using the helper, and add a signature block
before `document.close()`:

```java
    @Override
    @Transactional(readOnly = true)
    public ByteArrayInputStream generateInvoiceAuditPdf(UUID invoiceId, Authentication authentication) {
        log.info("Generating PDF Audit report for invoice: {}", invoiceId);
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        List<InvoiceStatusHistory> histories = historyRepository.findByInvoiceIdOrderByChangedAtAsc(invoiceId);
        Locale locale = LocaleContextHolder.getLocale();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
        ReportMetadata meta = buildMetadata(authentication, null, locale);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            com.oct.invoicesystem.shared.export.PdfBranding.addLetterhead(document);

            // Title
            document.add(new Paragraph(messageSource.getMessage("report.pdf.audit.title", null, locale))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(20)
                    .setBold());

            // Metadata header (generator + date; no period for a single-invoice audit)
            PdfMetadata.renderHeader(document, meta, messageSource, locale);

            document.add(new Paragraph("\n"));
```

Keep the invoice-summary table and audit-trail table exactly as they are. Just before
`document.close();` add:
```java
            PdfMetadata.renderSignatureBlock(document, messageSource, locale);
            document.close();
```

> The local `formatter` variable stays: it is still used to format the audit-trail row dates and
> `h.getChangedAt()`. Only the standalone "Generated at" paragraph is removed (now rendered by
> `PdfMetadata.renderHeader`).

Change `generateCompliancePdf` similarly. Replace its signature, build `periodLabel` from the
existing `report.pdf.period` message, and pass it to `buildMetadata`; drop the standalone period
+ generated-at paragraphs (now rendered once by the header); add the signature block before
`document.close()`:

```java
    @Override
    @Transactional(readOnly = true)
    public ByteArrayInputStream generateCompliancePdf(LocalDate startDate, LocalDate endDate, Authentication authentication) {
        log.info("Generating Compliance PDF report from {} to {}", startDate, endDate);
        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                null, null, startDate, endDate, null, null, Pageable.unpaged()).getContent();

        Locale locale = LocaleContextHolder.getLocale();
        String periodLabel = messageSource.getMessage("report.pdf.period",
                new Object[]{startDate.toString(), endDate.toString()}, locale);
        ReportMetadata meta = buildMetadata(authentication, periodLabel, locale);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            com.oct.invoicesystem.shared.export.PdfBranding.addLetterhead(document);

            // Title
            document.add(new Paragraph(messageSource.getMessage("report.pdf.compliance.title", null, locale))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(20)
                    .setBold());

            // Metadata header (period + generator + date, rendered once)
            PdfMetadata.renderHeader(document, meta, messageSource, locale);

            document.add(new Paragraph("\n"));
```
Keep the compliance table exactly as-is. Just before `document.close();` add:
```java
            PdfMetadata.renderSignatureBlock(document, messageSource, locale);
            document.close();
```

- [ ] **Step 5: Update the controller endpoints to pass `Authentication`**

In `ReportController.java`, add `Authentication` params and pass them through.

`exportAuditPdf`:
```java
    public ResponseEntity<Resource> exportAuditPdf(@PathVariable UUID id,
                                                   org.springframework.security.core.Authentication authentication) {
        ByteArrayInputStream stream = reportService.generateInvoiceAuditPdf(id, authentication);
```
`exportCompliancePdf`:
```java
    public ResponseEntity<Resource> exportCompliancePdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            org.springframework.security.core.Authentication authentication) {

        ByteArrayInputStream stream = reportService.generateCompliancePdf(startDate, endDate, authentication);
```

- [ ] **Step 6: Update the controller tests' mocks to the new signatures**

In `ReportControllerTest.java`:

`exportAuditPdf_WithDaf_ReturnsFile` — change the stub:
```java
        when(reportService.generateInvoiceAuditPdf(eq(id), any()))
                .thenReturn(new ByteArrayInputStream("fake pdf".getBytes()));
```
`exportCompliancePdf_WithDaf_ReturnsFile` — change the stub:
```java
        when(reportService.generateCompliancePdf(any(), any(), any()))
                .thenReturn(new ByteArrayInputStream("fake pdf".getBytes()));
```
Ensure `eq` and `any` are imported (`import static org.mockito.ArgumentMatchers.*;` is typically already present — verify).

- [ ] **Step 7: Run the affected tests**

```bash
./mvnw -q -Dtest=ReportServiceTest,ReportControllerTest test
```
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/report/service/ReportService.java \
        src/main/java/com/oct/invoicesystem/domain/report/service/ReportServiceImpl.java \
        src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java \
        src/test/java/com/oct/invoicesystem/domain/report/service/ReportServiceTest.java \
        src/test/java/com/oct/invoicesystem/domain/report/controller/ReportControllerTest.java
git commit -m "feat(reports): metadonnees + signature sur PDF audit & compliance"
```

---

## Task 5: Executive summary + custom builder run PDF (ReportBuilderService)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/service/ReportBuilderService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/report/service/ReportBuilderServiceTest.java`

**Interfaces:**
- Consumes: `ReportMetadata` (Task 2), the 6-arg `TabularExportService.export(...)` (Task 3), `SecurityHelper`.
- Produces:
  - `ReportBuilderService.run(UUID id, org.springframework.security.core.Authentication authentication)`
  - `ReportBuilderService.executiveSummaryPdf(org.springframework.security.core.Authentication authentication)`
  - Private helper `ReportMetadata builderMetadata(Authentication authentication, Locale locale)` (period = null).

- [ ] **Step 1: Write a failing test for executive summary metadata**

Add to `ReportBuilderServiceTest.java`. First add a `@Mock private com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;` field (and confirm `ReportBuilderService` will get a `securityHelper` constructor arg in Step 3). Then:

```java
    @Test
    void executiveSummaryPdf_passesMetadataToExport() {
        com.oct.invoicesystem.domain.user.model.Role role =
                com.oct.invoicesystem.domain.user.model.Role.builder().name("ROLE_DAF").build();
        com.oct.invoicesystem.domain.user.model.UserRole ur =
                com.oct.invoicesystem.domain.user.model.UserRole.builder().role(role).build();
        com.oct.invoicesystem.domain.user.model.User user =
                com.oct.invoicesystem.domain.user.model.User.builder()
                        .firstName("Jean").lastName("Dupont")
                        .userRoles(new java.util.HashSet<>(java.util.Set.of(ur)))
                        .build();
        org.springframework.security.core.Authentication auth =
                org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
        org.mockito.Mockito.when(securityHelper.currentUser(auth)).thenReturn(user);

        org.mockito.Mockito.when(reportService.getDashboardKpis())
                .thenReturn(new com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO(
                        0L, 0L, java.util.Map.of(), java.util.Map.of(), 0.0, 0.0, java.util.Map.of(), 0.0));
        org.mockito.Mockito.when(reportService.getBudgetVsActual())
                .thenReturn(new com.oct.invoicesystem.domain.report.dto.BudgetVsActualDTO(
                        java.util.List.of(), null, null, null));
        org.mockito.Mockito.when(messageSource.getMessage(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn("L");
        org.mockito.Mockito.when(exportService.export(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(com.oct.invoicesystem.shared.export.ReportMetadata.class),
                org.mockito.ArgumentMatchers.any())).thenReturn(new byte[]{'%','P','D','F'});

        byte[] out = service.executiveSummaryPdf(auth);
        org.junit.jupiter.api.Assertions.assertNotNull(out);

        org.mockito.ArgumentCaptor<com.oct.invoicesystem.shared.export.ReportMetadata> cap =
                org.mockito.ArgumentCaptor.forClass(com.oct.invoicesystem.shared.export.ReportMetadata.class);
        org.mockito.Mockito.verify(exportService).export(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                cap.capture(), org.mockito.ArgumentMatchers.any());
        org.junit.jupiter.api.Assertions.assertEquals("Dupont Jean", cap.getValue().generatorName());
        org.junit.jupiter.api.Assertions.assertNull(cap.getValue().periodLabel());
    }
```

> IMPORTANT: verify the exact constructor arity/param order of `DashboardKpiDTO` and
> `BudgetVsActualDTO` before running — read their record definitions and match the `new ...(...)`
> arguments to the real components (the placeholders above must be replaced with the real ones).
> If a builder exists, prefer it. This is a plan instruction, not a guess to copy blindly.

- [ ] **Step 2: Run to verify it fails**

```bash
./mvnw -q -Dtest=ReportBuilderServiceTest test
```
Expected: FAIL — `executiveSummaryPdf(Authentication)` and the `securityHelper` field don't exist yet.

- [ ] **Step 3: Implement in `ReportBuilderService`**

Add the dependency field (after `messageSource`, line ~45):
```java
    private final com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;
```
Add imports:
```java
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.export.ReportMetadata;
import org.springframework.security.core.Authentication;
import java.util.Locale;
import java.util.Objects;
```

Add the private helper:
```java
    /** Report metadata for builder PDFs: "LASTNAME Firstname" + localized role, no period. */
    private ReportMetadata builderMetadata(Authentication authentication, Locale locale) {
        User u = securityHelper.currentUser(authentication);
        String name = ((u.getLastName() == null ? "" : u.getLastName()) + " "
                + (u.getFirstName() == null ? "" : u.getFirstName())).trim();
        String roleCode = null;
        var names = u.getUserRoles() == null ? java.util.Set.<String>of()
                : u.getUserRoles().stream()
                    .map(ur -> ur.getRole() == null ? null : ur.getRole().getName())
                    .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (names.contains("ROLE_DAF")) roleCode = "ROLE_DAF";
        else if (names.contains("ROLE_ASSISTANT_COMPTABLE")) roleCode = "ROLE_ASSISTANT_COMPTABLE";
        else roleCode = names.stream().findFirst().orElse(null);
        String roleLabel = roleCode == null ? ""
                : messageSource.getMessage("report.pdf.role." + roleCode, null, roleCode, locale);
        return new ReportMetadata(name, roleLabel, java.time.Instant.now(), null);
    }
```

Change `run` to accept `Authentication` and pass metadata only for the PDF format:
```java
    @Transactional
    public byte[] run(UUID id, Authentication authentication) {
        ReportDefinition def = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report definition not found: " + id));
        byte[] bytes = render(def, authentication);
        def.setLastRunAt(Instant.now());
        repository.save(def);
        return bytes;
    }
```
Change `render` to accept `Authentication` and branch on format:
```java
    /** Renders the report's dataset into the chosen format. Used by run() and the scheduler. */
    public byte[] render(ReportDefinition def, Authentication authentication) {
        TabularExportService.Format fmt = TabularExportService.Format.from(def.getFormat());
        Dataset ds = buildDataset(def);
        if (fmt == TabularExportService.Format.PDF && authentication != null) {
            ReportMetadata meta = builderMetadata(authentication, org.springframework.context.i18n.LocaleContextHolder.getLocale());
            return exportService.export(fmt, ds.title(), ds.columns(), ds.rows(), meta, messageSource);
        }
        return exportService.export(fmt, ds.title(), ds.columns(), ds.rows());
    }
```

> Check callers of the old `render(ReportDefinition)`. The scheduler `ScheduledReportJob` calls
> `render(def)` (no auth). Update that call to `render(def, null)` so scheduled PDFs keep the
> current logo-only layout (a scheduled job has no interactive user). Verify by grepping
> `render(` across the codebase before compiling.

Change `executiveSummaryPdf`:
```java
    @Transactional(readOnly = true)
    public byte[] executiveSummaryPdf(Authentication authentication) {
        var kpi = reportService.getDashboardKpis();
        var budget = reportService.getBudgetVsActual();
        Locale locale = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        List<List<String>> rows = List.of(
                // ... unchanged rows ...
        );
        ReportMetadata meta = builderMetadata(authentication, locale);
        return exportService.export(TabularExportService.Format.PDF, "Executive Summary",
                List.of(
                        messageSource.getMessage("report.excel.header.indicator", null, locale),
                        messageSource.getMessage("report.excel.header.value", null, locale)
                ), rows, meta, messageSource);
    }
```
(Keep the existing `rows` list body verbatim — only the signature, the `locale` variable reuse, and the final `export(...)` call change.)

- [ ] **Step 4: Update the controller endpoints (run + executive-summary)**

In `ReportController.java`:

`runDefinition`:
```java
    public ResponseEntity<byte[]> runDefinition(@PathVariable UUID id,
                                                org.springframework.security.core.Authentication authentication) {
        byte[] body = reportBuilderService.run(id, authentication);
```
`executiveSummary`:
```java
    public ResponseEntity<byte[]> executiveSummary(org.springframework.security.core.Authentication authentication) {
        byte[] body = reportBuilderService.executiveSummaryPdf(authentication);
```

- [ ] **Step 5: Fix the scheduler call site**

In `src/main/java/com/oct/invoicesystem/domain/report/scheduler/ScheduledReportJob.java`, change the `render(def)` call to `render(def, null)`.

```bash
grep -rn "\.render(" src/main/java
```
Expected: only the scheduler (and internal `run`) call `render`; ensure all compile with the new 2-arg signature.

- [ ] **Step 6: Run the affected tests**

```bash
./mvnw -q -Dtest=ReportBuilderServiceTest,ReportControllerTest test
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/report/service/ReportBuilderService.java \
        src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java \
        src/main/java/com/oct/invoicesystem/domain/report/scheduler/ScheduledReportJob.java \
        src/test/java/com/oct/invoicesystem/domain/report/service/ReportBuilderServiceTest.java
git commit -m "feat(reports): metadonnees + signature sur executive summary & run PDF M11"
```

---

## Task 6: Full suite + docs update

**Files:**
- Modify: `docs/TASKS.md` (module status), `docs/KNOWN_ISSUES_REGISTRY.md` only if a bug was found and fixed during implementation.

- [ ] **Step 1: Run the full test suite**

```bash
./mvnw test
```
Expected: 0 failures, 0 errors. Total should be baseline + the new tests (≥ 567 passing, 0/0).

- [ ] **Step 2: Update `docs/TASKS.md`**

Record under the reports module that PDF reports now carry generator (last+first name + role),
generation date, signature box, period (where applicable), and the official OCT logo. Note the
scope was PDF-only (Excel/CSV unchanged).

- [ ] **Step 3: Commit docs**

```bash
git add docs/TASKS.md
git commit -m "docs(reports): TASKS.md — metadonnees/signature/logo PDF (PDF-only)"
```

- [ ] **Step 4: STOP — report to the user**

Do NOT push or merge to `main`. Report: branch `feat/reports-pdf-metadata`, full suite green,
and ask for the user's go-ahead on runtime verification (browser: download each PDF and confirm
the metadata block, signature box, and new logo render) and on push/PR.

---

## Self-Review Notes

- **Spec coverage:** generator name+role (Tasks 2,4,5) · generation date (Task 3 header) · signature box (Task 3) · period, optional (Tasks 3,4) · logo replacement (Task 1) · toPdf parameterizable/optional (Task 3) · Authentication threaded from controllers (Tasks 4,5) · role i18n backend keys (Task 2) · FR ISO-8859-1 handling (Task 2) · @PreAuthorize preserved (Tasks 4,5) · preview confirmed out of scope (no task, documented in spec §2).
- **Name order:** last name then first name — Global Constraints + Tasks 4/5 helpers + Task 5 assertion (`"Dupont Jean"`).
- **Backward compat:** 4-arg `export` and scheduled `render(def, null)` keep the legacy logo-only PDF (Tasks 3,5).
- **Type consistency:** `ReportMetadata(generatorName, generatorRole, generatedAt, periodLabel)` used identically in Tasks 2–5. `render(ReportDefinition, Authentication)` and `run(UUID, Authentication)` consistent between Task 5 impl and controller.
- **Verify-before-code flags:** `UserRole` builder field name, `DashboardKpiDTO` / `BudgetVsActualDTO` constructor arity (Task 5 Step 1), and all `render(` call sites (Task 5 Step 5) must be checked against real code before compiling — called out inline.
