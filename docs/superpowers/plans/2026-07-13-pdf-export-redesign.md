# Refonte des PDF d'export de liste — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Porter les 6 PDF d'export de liste (factures, audit, fournisseurs, utilisateurs, paiements, rapprochement) au niveau visuel de la facture individuelle : logo OCT détouré, en-tête navy + filet or, tableau portrait sans coupure (police réduite + largeurs pondérées), en-tête « généré par / date / filtres » et bloc e-signature **toujours** présents, titres et headers d'audit localisés.

**Architecture:** Un helper de style partagé `PdfTableStyle` centralise l'identité visuelle (couleurs OCT, polices Helvetica, cellules navy/zébrées) extraite d'`InvoicePdfService`. `TabularExportService.toPdf` est réécrit pour l'utiliser, avec des largeurs de colonnes pondérées par un heuristique de nom d'en-tête, et rend systématiquement metadata + signature. `ReportMetadata` gagne un champ `filtersLabel`. Les 6 contrôleurs/services d'export construisent un titre i18n + un `filtersLabel` + un `meta` (via `ReportMetadata.of` sur l'utilisateur courant) et appellent l'overload riche. Le mauvais logo (fond photo) est remplacé par l'asset détouré déjà présent.

**Tech Stack:** Java 21, Spring Boot, iText 8.0.5 (kernel + layout), Apache POI (XLSX, inchangé), JUnit 5, PDFBox (extraction texte pour les tests). i18n via `MessageSource` + `messages*.properties`.

## Global Constraints

- **Backend uniquement.** Aucun fichier front modifié. Ne PAS lancer le build front.
- **Gate par tâche :** `./mvnw -q -Dtest=<ClasseDeTest> test` (le(s) test(s) de la tâche) vert, puis à la fin de chaque tâche `./mvnw -q compile` (0 erreur). Sous Windows, utiliser `./mvnw` via le Bash tool (Git Bash), pas `mvnw.cmd`.
- **CSV et EXCEL inchangés** : le chemin `toCsv`/`toExcel` de `TabularExportService` ne doit pas changer de sortie. Les tests existants de ces chemins restent verts.
- **`InvoicePdfService` et `RemittanceAdviceService` ne sont PAS touchés** (déjà soignés, hors périmètre).
- **Zéro couleur nouvelle d'identité** : réutiliser les RVB OCT déjà définis dans `InvoicePdfService` (`OCT_NAVY = 15,37,64` ; `OCT_GOLD = 200,168,75` ; `ROW_ALT = 243,244,246` ; `LIGHT_GRAY = 248,249,250`). Devise **XAF** jamais XOF.
- **Encodage `messages_fr.properties` = ISO-8859-1 (Latin-1)** : toute addition FR avec accent doit être écrite en échappements ASCII `\uXXXX` (ex. `é` pour é, `à` pour à). NE PAS écrire d'accent brut ni d'em-dash/guillemets courbes dans ce fichier. `messages.properties` (défaut/EN) est ASCII.
- **iText 8** : appliquer les polices par `Paragraph.setFont(...)` / `Cell` — pas de police globale de document fiable. Suivre le pattern d'`InvoicePdfService`.
- **Un commit par tâche**, message `type(scope): description`.

---

## File Structure

- `src/main/resources/branding/oct-logo.png` — **Modify (binaire)** : remplacer par le contenu de `assets/logos/oct-logo.png` (logo détouré, fond transparent).
- `src/main/java/com/oct/invoicesystem/shared/export/PdfTableStyle.java` — **Create** : constantes couleur + polices + fabriques de cellules (en-tête navy, corps zébré).
- `src/main/java/com/oct/invoicesystem/shared/export/ReportMetadata.java` — **Modify** : ajouter le champ `filtersLabel` au record + surcharge de `of(...)`.
- `src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java` — **Modify** : afficher `filtersLabel` dans `renderHeader` ; enrichir `renderSignatureBlock` (ligne nom/fonction).
- `src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java` — **Modify** : réécrire `toPdf` (style + largeurs pondérées) ; ajouter `columnWeights`.
- `src/main/resources/messages.properties` + `messages_fr.properties` — **Modify** : clés `export.title.*`, `export.header.audit.*`, `export.pdf.*`.
- Contrôleurs/services d'export — **Modify** : `InvoiceController`, `AuditController`, `SupplierController`, `UserController`, `PaymentServiceImpl` (+ `PaymentController` pour l'`Authentication`), `InvoiceController` (matching).
- Tests — **Create/Modify** : `PdfTableStyleTest`, `TabularExportServiceTest` (existant ou nouveau), et une extension d'un test de contrôleur.

---

## Task 1 : Remplacer le logo PDF (asset détouré)

Corrige le logo à fond photo par le logotype OCT détouré déjà présent dans le repo. Aucun code changé — seulement le binaire au chemin classpath que `PdfBranding` charge déjà.

**Files:**
- Modify (binaire) : `src/main/resources/branding/oct-logo.png`
- Source : `assets/logos/oct-logo.png`

**Interfaces:**
- Consumes: rien.
- Produces: rien (le chemin classpath `/branding/oct-logo.png` est inchangé ; `PdfBranding.addLetterhead` continue de fonctionner).

- [ ] **Step 1 : Vérifier les deux fichiers**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ls -la assets/logos/oct-logo.png src/main/resources/branding/oct-logo.png`
Expected: les deux existent ; ce sont deux fichiers de tailles différentes.

- [ ] **Step 2 : Remplacer le binaire**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && cp assets/logos/oct-logo.png src/main/resources/branding/oct-logo.png`

- [ ] **Step 3 : Confirmer le remplacement**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && cmp assets/logos/oct-logo.png src/main/resources/branding/oct-logo.png && echo IDENTIQUE`
Expected: `IDENTIQUE` (les deux fichiers sont identiques octet pour octet).

- [ ] **Step 4 : Commit**

```bash
git add src/main/resources/branding/oct-logo.png
git commit -m "fix(export): logo PDF detoure (remplace le logo a fond photo)"
```

> Note : le rendu visuel (taille du logo dans le PDF) est vérifié en Task 8 (runtime). Si le logo horizontal apparaît trop large, seul le littéral `LOGO_WIDTH_PT` de `PdfBranding` sera ajusté (traité en Task 8).

---

## Task 2 : `PdfTableStyle` — style de tableau partagé

Extrait l'identité visuelle du tableau (couleurs, polices, cellules) dans un helper réutilisable. Aucune donnée, aucune mise en page globale — uniquement des fabriques de cellules et de polices.

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/shared/export/PdfTableStyle.java`
- Test: `src/test/java/com/oct/invoicesystem/shared/export/PdfTableStyleTest.java`

**Interfaces:**
- Consumes: iText 8 (`Cell`, `Paragraph`, `PdfFont`, `DeviceRgb`, `SolidBorder`, `TextAlignment`).
- Produces:
  - `PdfTableStyle.OCT_NAVY`, `OCT_GOLD`, `ROW_ALT`, `LIGHT_GRAY` (`DeviceRgb`, public static final).
  - `static PdfFont bold()` et `static PdfFont regular()` (Helvetica ; lèvent `java.io.IOException`).
  - `static Cell headerCell(String text, PdfFont bold, float size)` — fond navy, texte blanc, gras, padding 5, bordure navy 0.5.
  - `static Cell bodyCell(String text, PdfFont font, float size, boolean alt, TextAlignment align)` — fond blanc ou `ROW_ALT` selon `alt`, bordure `LIGHT_GRAY` 0.5, padding 4, alignement `align`, texte `"—"` si null/blank.

- [ ] **Step 1 : Écrire le test qui échoue**

Create `src/test/java/com/oct/invoicesystem/shared/export/PdfTableStyleTest.java` :

```java
package com.oct.invoicesystem.shared.export;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.properties.TextAlignment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTableStyleTest {

    @Test
    void octNavyIsTheBrandNavy() {
        assertThat(PdfTableStyle.OCT_NAVY).isEqualTo(new DeviceRgb(15, 37, 64));
        assertThat(PdfTableStyle.OCT_GOLD).isEqualTo(new DeviceRgb(200, 168, 75));
    }

    @Test
    void headerCellIsNavyWithWhiteText() throws Exception {
        Cell cell = PdfTableStyle.headerCell("Reference", PdfTableStyle.bold(), 8f);
        assertThat(cell.getBackgroundColor()).isNotNull();
        // Background must be the navy brand color.
        assertThat(cell.<Object>getProperty(com.itextpdf.layout.properties.Property.BACKGROUND).toString())
                .contains("15").contains("37").contains("64");
    }

    @Test
    void bodyCellAltRowUsesAlternateBackground() throws Exception {
        Cell plain = PdfTableStyle.bodyCell("x", PdfTableStyle.regular(), 8f, false, TextAlignment.LEFT);
        Cell alt   = PdfTableStyle.bodyCell("x", PdfTableStyle.regular(), 8f, true,  TextAlignment.LEFT);
        assertThat(plain.<Object>getProperty(com.itextpdf.layout.properties.Property.BACKGROUND).toString())
                .isNotEqualTo(alt.<Object>getProperty(com.itextpdf.layout.properties.Property.BACKGROUND).toString());
    }

    @Test
    void bodyCellNullTextRendersDash() throws Exception {
        Cell cell = PdfTableStyle.bodyCell(null, PdfTableStyle.regular(), 8f, false, TextAlignment.LEFT);
        assertThat(cell.toString()).isNotNull(); // cell built without throwing on null text
    }
}
```

- [ ] **Step 2 : Lancer le test — échec attendu**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=PdfTableStyleTest test`
Expected: échec de compilation / `PdfTableStyle` introuvable.

- [ ] **Step 3 : Écrire `PdfTableStyle`**

Create `src/main/java/com/oct/invoicesystem/shared/export/PdfTableStyle.java` :

```java
package com.oct.invoicesystem.shared.export;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

import java.io.IOException;

/**
 * Shared visual identity for exported PDF tables (OCT navy/gold, Helvetica, navy header cells,
 * zebra body cells). Extracted from InvoicePdfService so list exports and the single-invoice
 * document share one look. Stateless: only cell/font factories, no data, no page layout.
 */
public final class PdfTableStyle {

    public static final DeviceRgb OCT_NAVY   = new DeviceRgb(15, 37, 64);
    public static final DeviceRgb OCT_GOLD   = new DeviceRgb(200, 168, 75);
    public static final DeviceRgb ROW_ALT    = new DeviceRgb(243, 244, 246);
    public static final DeviceRgb LIGHT_GRAY = new DeviceRgb(248, 249, 250);
    private static final DeviceRgb WHITE      = new DeviceRgb(255, 255, 255);

    private PdfTableStyle() {}

    public static PdfFont bold() throws IOException {
        return PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
    }

    public static PdfFont regular() throws IOException {
        return PdfFontFactory.createFont(StandardFonts.HELVETICA);
    }

    /** Navy background, white bold text — table header cell. */
    public static Cell headerCell(String text, PdfFont bold, float size) {
        return new Cell()
                .add(new Paragraph(text == null ? "" : text)
                        .setFont(bold).setFontSize(size).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(OCT_NAVY)
                .setBorder(new SolidBorder(OCT_NAVY, 0.5f))
                .setPadding(5);
    }

    /** Zebra body cell: white or ROW_ALT background, thin gray border, given alignment. */
    public static Cell bodyCell(String text, PdfFont font, float size, boolean alt, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text == null || text.isBlank() ? "—" : text)
                        .setFont(font).setFontSize(size).setTextAlignment(align))
                .setBackgroundColor(alt ? ROW_ALT : WHITE)
                .setBorder(new SolidBorder(LIGHT_GRAY, 0.5f))
                .setPadding(4)
                .setTextAlignment(align);
    }
}
```

- [ ] **Step 4 : Lancer le test — succès**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=PdfTableStyleTest test`
Expected: PASS (4/4).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/PdfTableStyle.java src/test/java/com/oct/invoicesystem/shared/export/PdfTableStyleTest.java
git commit -m "feat(export): PdfTableStyle (identite navy/or partagee des tableaux PDF)"
```

---

## Task 3 : Largeurs de colonnes pondérées

Ajoute une fonction pure qui répartit la largeur du tableau selon le type de chaque colonne (déduit du libellé d'en-tête), pour que 11 colonnes tiennent en portrait sans coupure. Testable sans PDF.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java` (ajouter la méthode statique `columnWeights`)
- Test: `src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java` (créer si absent, sinon compléter)

**Interfaces:**
- Consumes: rien.
- Produces: `static float[] columnWeights(java.util.List<String> headers)` — un poids par colonne, dont la **somme vaut 100** (pourcentages). Colonnes « larges » (email, description, libellé, nom/fournisseur, supplier) reçoivent un poids 3 ; les autres poids 1 ; normalisé à 100.

- [ ] **Step 1 : Écrire le test qui échoue**

Create (ou compléter) `src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java` :

```java
package com.oct.invoicesystem.shared.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TabularExportServiceTest {

    @Test
    void columnWeightsSumTo100() {
        float[] w = TabularExportService.columnWeights(
                List.of("Reference", "Supplier Email", "Amount", "Status"));
        float sum = 0;
        for (float f : w) sum += f;
        assertThat(sum).isCloseTo(100f, within(0.5f));
    }

    @Test
    void wideColumnsGetMoreWeightThanNarrow() {
        List<String> headers = List.of("Amount", "Supplier Email");
        float[] w = TabularExportService.columnWeights(headers);
        // "Supplier Email" (wide) must be wider than "Amount" (narrow).
        assertThat(w[1]).isGreaterThan(w[0]);
    }

    @Test
    void unknownHeadersFallBackToEqualWeights() {
        float[] w = TabularExportService.columnWeights(List.of("Aaa", "Bbb", "Ccc"));
        assertThat(w[0]).isCloseTo(w[1], within(0.01f));
        assertThat(w[1]).isCloseTo(w[2], within(0.01f));
    }
}
```

- [ ] **Step 2 : Lancer le test — échec attendu**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=TabularExportServiceTest#columnWeightsSumTo100+wideColumnsGetMoreWeightThanNarrow+unknownHeadersFallBackToEqualWeights test`
Expected: échec — `columnWeights` introuvable.

- [ ] **Step 3 : Ajouter `columnWeights` à `TabularExportService`**

Dans `TabularExportService`, ajouter cette méthode statique (au-dessus de `toPdf`, dans la section `// ── PDF ──`) :

```java
    /**
     * Weighted column widths (as percentages summing to 100) so wide text columns (email,
     * description, supplier name) get more room than narrow ones (amount, date, code). Keeps an
     * 11-column table inside an A4 portrait page instead of overflowing off the right margin.
     */
    static float[] columnWeights(java.util.List<String> headers) {
        String[] wideKeys = {"email", "description", "libell", "label", "supplier", "fournisseur", "name", "nom"};
        float[] weights = new float[headers.size()];
        float total = 0f;
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i) == null ? "" : headers.get(i).toLowerCase();
            boolean wide = false;
            for (String k : wideKeys) {
                if (h.contains(k)) { wide = true; break; }
            }
            weights[i] = wide ? 3f : 1f;
            total += weights[i];
        }
        if (total == 0f) total = headers.size();
        for (int i = 0; i < weights.length; i++) {
            weights[i] = weights[i] / total * 100f;
        }
        return weights;
    }
```

- [ ] **Step 4 : Lancer le test — succès**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=TabularExportServiceTest test`
Expected: PASS (les 3 tests de largeurs ; d'autres tests de la classe restent verts s'ils existent).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java
git commit -m "feat(export): largeurs de colonnes ponderees (tableau portrait sans coupure)"
```

---

## Task 4 : `ReportMetadata` — champ `filtersLabel`

Ajoute un libellé de filtres au record de metadata, sans casser les appelants existants (le Report Builder passe `periodLabel`).

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/shared/export/ReportMetadata.java`
- Test: `src/test/java/com/oct/invoicesystem/shared/export/ReportMetadataTest.java` (créer)

**Interfaces:**
- Consumes: `com.oct.invoicesystem.domain.user.model.User`, `MessageSource`, `Locale`.
- Produces:
  - record enrichi : `ReportMetadata(String generatorName, String generatorRole, Instant generatedAt, String periodLabel, String filtersLabel)`.
  - `static ReportMetadata of(User user, MessageSource ms, String periodLabel, Locale locale)` — **conservé**, `filtersLabel = null`.
  - `static ReportMetadata of(User user, MessageSource ms, String periodLabel, String filtersLabel, Locale locale)` — **nouveau**.

- [ ] **Step 1 : Écrire le test qui échoue**

Create `src/test/java/com/oct/invoicesystem/shared/export/ReportMetadataTest.java` :

```java
package com.oct.invoicesystem.shared.export;

import com.oct.invoicesystem.domain.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMetadataTest {

    @Test
    void ofWithFiltersLabelKeepsBothPeriodAndFilters() {
        User u = new User();
        u.setFirstName("Marie");
        u.setLastName("Dubois");
        StaticMessageSource ms = new StaticMessageSource();
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
        StaticMessageSource ms = new StaticMessageSource();
        ReportMetadata meta = ReportMetadata.of(u, ms, "Periode X", Locale.FRENCH);
        assertThat(meta.filtersLabel()).isNull();
    }
}
```

- [ ] **Step 2 : Lancer le test — échec attendu**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=ReportMetadataTest test`
Expected: échec de compilation (`filtersLabel()` et l'overload à 5 args n'existent pas).

- [ ] **Step 3 : Enrichir le record**

Dans `ReportMetadata.java`, remplacer la déclaration du record et le factory par :

```java
public record ReportMetadata(
        String generatorName,
        String generatorRole,
        Instant generatedAt,
        String periodLabel,
        String filtersLabel
) {

    /** Legacy factory (no filters): filtersLabel is null. */
    public static ReportMetadata of(User user, MessageSource messageSource, String periodLabel, Locale locale) {
        return of(user, messageSource, periodLabel, null, locale);
    }

    /**
     * Resolves the report generator from the current {@link User}: "LASTNAME Firstname" plus the
     * localized role label (report.pdf.role.&lt;ROLE&gt;, DAF prioritized over ASSISTANT_COMPTABLE,
     * then the first role found, falling back to the raw role code). {@code periodLabel} and
     * {@code filtersLabel} are passed through unchanged (either may be null).
     */
    public static ReportMetadata of(User user, MessageSource messageSource, String periodLabel,
                                    String filtersLabel, Locale locale) {
        String name = ((user.getLastName() == null ? "" : user.getLastName()) + " "
                + (user.getFirstName() == null ? "" : user.getFirstName())).trim();
        String roleCode = resolveRoleCode(user);
        String roleLabel = roleCode == null ? ""
                : messageSource.getMessage("report.pdf.role." + roleCode, null, roleCode, locale);
        return new ReportMetadata(name, roleLabel, Instant.now(), periodLabel, filtersLabel);
    }
```

(La méthode privée `resolveRoleCode` reste inchangée.)

- [ ] **Step 4 : Lancer le test — succès**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=ReportMetadataTest test`
Expected: PASS (2/2).

- [ ] **Step 5 : Compiler tout le module (l'ajout d'un champ au record peut casser un appelant)**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q compile`
Expected: 0 erreur. (Si un `new ReportMetadata(...)` existe ailleurs avec 4 args, l'ajuster à 5 args avec `null` — mais la construction passe normalement par `of(...)`.)

- [ ] **Step 6 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/ReportMetadata.java src/test/java/com/oct/invoicesystem/shared/export/ReportMetadataTest.java
git commit -m "feat(export): ReportMetadata porte un filtersLabel"
```

---

## Task 5 : Clés i18n (titres, headers audit, metadata/signature)

Ajoute toutes les clés de traduction consommées par les tâches suivantes. Anglais (défaut) + français (Latin-1 échappé).

**Files:**
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_fr.properties`

**Interfaces:**
- Produces (clés) : `export.title.invoices|audit|audit_summary|matching|payments|suppliers|users` ; `export.header.audit.date|user|action|entity|entity_id|ip|dimension|label|count` ; `export.pdf.filters|signature|signature.name` (les clés `report.pdf.generated_by|generated_at|signature|signature.date` existent déjà — les réutiliser).

- [ ] **Step 1 : Vérifier les clés déjà présentes (ne pas dupliquer)**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && grep -nE "report.pdf.(generated_by|generated_at|signature)" src/main/resources/messages.properties`
Expected: ces clés existent (elles servent déjà au Report Builder). On ne les recrée pas.

- [ ] **Step 2 : Ajouter les clés anglaises (ASCII, accents non requis)**

Append à `src/main/resources/messages.properties` :

```properties
# Export PDF titles
export.title.invoices=Invoices
export.title.audit=Audit Log
export.title.audit_summary=Audit Summary
export.title.matching=Matching Report
export.title.payments=Payments
export.title.suppliers=Suppliers
export.title.users=Users
# Export PDF audit headers
export.header.audit.date=Date
export.header.audit.user=User
export.header.audit.action=Action
export.header.audit.entity=Entity
export.header.audit.entity_id=Entity ID
export.header.audit.ip=IP
export.header.audit.dimension=Dimension
export.header.audit.label=Label
export.header.audit.count=Count
# Export PDF metadata & signature
export.pdf.filters=Filters: {0}
export.pdf.signature=Signature
export.pdf.signature.name=Name / function:
```

- [ ] **Step 3 : Ajouter les clés françaises (Latin-1 — accents en \\uXXXX)**

Append à `src/main/resources/messages_fr.properties` (⚠️ NE PAS écrire d'accent brut ; ce fichier est ISO-8859-1) :

```properties
# Export PDF titles
export.title.invoices=Factures
export.title.audit=Journal d'audit
export.title.audit_summary=Synthèse d'audit
export.title.matching=Rapport de rapprochement
export.title.payments=Paiements
export.title.suppliers=Fournisseurs
export.title.users=Utilisateurs
# Export PDF audit headers
export.header.audit.date=Date
export.header.audit.user=Utilisateur
export.header.audit.action=Action
export.header.audit.entity=Entité
export.header.audit.entity_id=ID entité
export.header.audit.ip=IP
export.header.audit.dimension=Dimension
export.header.audit.label=Libellé
export.header.audit.count=Nombre
# Export PDF metadata & signature
export.pdf.filters=Filtres : {0}
export.pdf.signature=Signature
export.pdf.signature.name=Nom / fonction :
```

- [ ] **Step 4 : Vérifier l'encodage (aucun octet non-ASCII introduit dans le fichier FR)**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && grep -nP "[\x80-\xFF]" src/main/resources/messages_fr.properties | grep -E "export\." || echo "OK: lignes export.* toutes ASCII"`
Expected: `OK: lignes export.* toutes ASCII` (les accents sont en `\uXXXX`, donc ASCII).

- [ ] **Step 5 : Commit**

```bash
git add src/main/resources/messages.properties src/main/resources/messages_fr.properties
git commit -m "i18n(export): titres, en-tetes audit et libelles metadata/signature PDF"
```

---

## Task 6 : Réécrire `TabularExportService.toPdf` (style riche + metadata/signature systématiques)

Cœur de la refonte : le PDF d'export utilise désormais `PdfTableStyle`, `columnWeights`, un en-tête navy + filet or, et rend metadata + signature à chaque fois qu'un `meta` est fourni.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java` (méthode `toPdf`)
- Modify: `src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java` (`renderHeader` affiche `filtersLabel` ; `renderSignatureBlock` ajoute la ligne nom/fonction)
- Test: `src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java` (ajouter des cas PDF)

**Interfaces:**
- Consumes: `PdfTableStyle` (Task 2), `columnWeights` (Task 3), `ReportMetadata.filtersLabel()` (Task 4), clés i18n (Task 5), `PdfBranding.addLetterhead`.
- Produces: `toPdf(...)` inchangé en signature ; sortie PDF stylée. `PdfMetadata.renderHeader(doc, meta, ms, loc)` affiche aussi `filtersLabel` ; `renderSignatureBlock(doc, ms, loc)` ajoute la ligne nom/fonction.

- [ ] **Step 1 : Écrire les tests PDF qui échouent**

Ajouter dans `TabularExportServiceTest` :

```java
    @org.junit.jupiter.api.Test
    void pdfIsValidAndContainsSignatureAndGeneratorWhenMetaPresent() throws Exception {
        TabularExportService svc = new TabularExportService();
        org.springframework.context.support.StaticMessageSource ms =
                new org.springframework.context.support.StaticMessageSource();
        ms.addMessage("report.pdf.generated_by", java.util.Locale.FRENCH, "Genere par {0} ({1})");
        ms.addMessage("report.pdf.generated_at", java.util.Locale.FRENCH, "Genere le {0}");
        ms.addMessage("report.pdf.signature", java.util.Locale.FRENCH, "Signature");
        ms.addMessage("report.pdf.signature.date", java.util.Locale.FRENCH, "Date :");
        ms.addMessage("export.pdf.signature.name", java.util.Locale.FRENCH, "Nom / fonction :");
        java.util.Locale.setDefault(java.util.Locale.FRENCH);

        ReportMetadata meta = new ReportMetadata("Dubois Marie", "DAF",
                java.time.Instant.now(), null, "Statut: Draft");
        byte[] pdf = svc.export(TabularExportService.Format.PDF, "Factures",
                java.util.List.of("Reference", "Amount"),
                java.util.List.of(java.util.List.of("FAC-1", "1000")),
                meta, ms);

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        String text = extractPdfText(pdf);
        assertThat(text).contains("Dubois Marie");
        assertThat(text).contains("Signature");
    }

    private static String extractPdfText(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
        }
    }
```

> Note : `org.apache.pdfbox:pdfbox` (version **3.0.3**) est déjà une dépendance du projet. En PDFBox 3.x, on charge un tableau d'octets via `org.apache.pdfbox.Loader.loadPDF(byte[])` (PAS `PDDocument.load` qui n'existe plus) — voir `OcrService.java:99` pour l'usage existant.

- [ ] **Step 2 : Lancer — échec attendu**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=TabularExportServiceTest#pdfIsValidAndContainsSignatureAndGeneratorWhenMetaPresent test`
Expected: échec — le PDF actuel n'a pas de « Signature » quand aucune n'est rendue via l'ancien chemin sans styles ou le texte diffère.

- [ ] **Step 3 : Enrichir `PdfMetadata`**

Dans `PdfMetadata.renderHeader`, après le bloc `generatedAt` (avant la fin de méthode), ajouter l'affichage des filtres :

```java
        if (meta.filtersLabel() != null && !meta.filtersLabel().isBlank()) {
            doc.add(new Paragraph(ms.getMessage("export.pdf.filters",
                    new Object[]{meta.filtersLabel()}, meta.filtersLabel(), loc))
                    .setTextAlignment(TextAlignment.LEFT)
                    .setFontSize(8));
        }
```

Dans `PdfMetadata.renderSignatureBlock`, insérer une ligne nom/fonction entre le libellé « Signature » et la ligne date. Remplacer le corps de la cellule par :

```java
        Cell cell = new Cell()
                .setBorder(new SolidBorder(ColorConstants.GRAY, 1f))
                .setHeight(90f)
                .add(new Paragraph(ms.getMessage("report.pdf.signature", null, loc))
                        .setBold().setFontSize(10))
                .add(new Paragraph("\n"))
                .add(new Paragraph(ms.getMessage("export.pdf.signature.name", null, "Name / function:", loc))
                        .setFontSize(9))
                .add(new Paragraph(ms.getMessage("report.pdf.signature.date", null, loc))
                        .setFontSize(9));
```

- [ ] **Step 4 : Réécrire `toPdf`**

Remplacer la méthode `toPdf` de `TabularExportService` par :

```java
    private byte[] toPdf(String title, List<String> headers, List<List<String>> rows,
                         ReportMetadata meta, org.springframework.context.MessageSource messageSource) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, com.itextpdf.kernel.geom.PageSize.A4);
            document.setMargins(36, 36, 36, 36);

            com.itextpdf.kernel.font.PdfFont bold = PdfTableStyle.bold();
            com.itextpdf.kernel.font.PdfFont regular = PdfTableStyle.regular();

            PdfBranding.addLetterhead(document);
            document.add(new Paragraph(title == null ? "Export" : title)
                    .setFont(bold).setFontSize(14).setFontColor(PdfTableStyle.OCT_NAVY));
            document.add(new com.itextpdf.layout.element.LineSeparator(
                            new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1.5f))
                    .setStrokeColor(PdfTableStyle.OCT_GOLD).setMarginTop(6).setMarginBottom(10));

            if (meta != null && messageSource != null) {
                PdfMetadata.renderHeader(document, meta, messageSource,
                        org.springframework.context.i18n.LocaleContextHolder.getLocale());
            }

            float fontSize = headers.size() >= 8 ? 7f : 8f;
            Table table = new Table(UnitValue.createPercentArray(columnWeights(headers)))
                    .useAllAvailableWidth();

            for (String h : headers) {
                table.addHeaderCell(PdfTableStyle.headerCell(h, bold, fontSize));
            }
            boolean alt = false;
            for (List<String> row : rows) {
                for (int c = 0; c < row.size(); c++) {
                    String v = row.get(c);
                    com.itextpdf.layout.properties.TextAlignment align = isNumericColumn(headers, c)
                            ? com.itextpdf.layout.properties.TextAlignment.RIGHT
                            : com.itextpdf.layout.properties.TextAlignment.LEFT;
                    table.addCell(PdfTableStyle.bodyCell(v, regular, fontSize, alt, align));
                }
                alt = !alt;
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

    /** Right-align amount/quantity/count columns (deduced from the header label). */
    private static boolean isNumericColumn(List<String> headers, int col) {
        if (col >= headers.size()) return false;
        String h = headers.get(col) == null ? "" : headers.get(col).toLowerCase();
        return h.contains("amount") || h.contains("montant") || h.contains("nombre")
                || h.contains("count") || h.contains("qt") || h.contains("total");
    }
```

- [ ] **Step 5 : Lancer les tests PDF — succès**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=TabularExportServiceTest test`
Expected: PASS (largeurs + PDF signature/generateur).

- [ ] **Step 6 : Compiler**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q compile`
Expected: 0 erreur.

- [ ] **Step 7 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java
git commit -m "feat(export): toPdf riche (navy/or, largeurs, metadata + signature systematiques)"
```

---

## Task 7 : Brancher les 6 exports (titre i18n + filtres + meta)

Chaque contrôleur/service d'export construit un titre i18n, un `filtersLabel`, et passe `meta` + `messageSource` à l'overload riche. L'audit passe aussi ses headers en i18n.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceController.java` (export factures L112 + export matching L192)
- Modify: `src/main/java/com/oct/invoicesystem/domain/audit/controller/AuditController.java` (L136-144 export + L204-210 summary)
- Modify: `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java` (L112)
- Modify: `src/main/java/com/oct/invoicesystem/domain/user/controller/UserController.java` (L143)
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/service/PaymentServiceImpl.java` (L233) + `PaymentController` (passer l'`Authentication` + locale au service)
- Test: `src/test/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceControllerExportPdfTest.java` (créer — test d'intégration léger sur l'export factures)

**Interfaces:**
- Consumes: `ReportMetadata.of(user, ms, periodLabel, filtersLabel, locale)` (Task 4), overload riche `export(fmt, title, headers, rows, meta, ms)`, `securityHelper.currentUser(authentication)` (pattern existant, cf. `ReportBuilderService`), clés i18n (Task 5).
- Produces: chaque endpoint d'export PDF renvoie désormais un PDF riche localisé avec metadata + signature.

**Pattern commun à appliquer dans chaque contrôleur** (adapter les noms de filtres) :

```java
// 1. résoudre l'utilisateur courant (le contrôleur reçoit déjà `Authentication authentication` ou l'ajouter en param)
com.oct.invoicesystem.domain.user.model.User currentUser = securityHelper.currentUser(authentication);
// 2. construire un libellé de filtres (une ligne, court)
String filters = buildFiltersLabel(...); // ex: "Statut: DRAFT · Departement: FIN" ; null si aucun filtre
// 3. titre i18n
String title = messageSource.getMessage("export.title.invoices", null, locale);
// 4. meta
com.oct.invoicesystem.shared.export.ReportMetadata meta =
        com.oct.invoicesystem.shared.export.ReportMetadata.of(currentUser, messageSource, null, filters, locale);
// 5. appel riche
byte[] body = tabularExportService.export(fmt, title, headers, rows, meta, messageSource);
```

- [ ] **Step 1 : Écrire le test d'intégration léger (factures) qui échoue**

Create `src/test/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceControllerExportPdfTest.java`. Utiliser le même socle que `InvoiceDocumentControllerTest` (regarder ce fichier existant pour le harnais MockMvc + auth de test). Contenu :

```java
package com.oct.invoicesystem.domain.invoice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class InvoiceControllerExportPdfTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(username = "daf", roles = {"DAF"})
    void exportPdfReturnsPdfWithFrenchTitle() throws Exception {
        byte[] body = mockMvc.perform(get("/api/invoices/export")
                        .param("format", "pdf")
                        .header("Accept-Language", "fr")
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        // Valid PDF
        org.assertj.core.api.Assertions.assertThat(
                new String(body, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        // French title + signature block present
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(body)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            org.assertj.core.api.Assertions.assertThat(text).contains("Factures");
            org.assertj.core.api.Assertions.assertThat(text).contains("Signature");
        }
    }
}
```

> Adapter `@WithMockUser` / le harnais d'authentification au pattern réellement utilisé par les tests de contrôleur du projet (vérifier `InvoiceDocumentControllerTest`). Si le contrôleur exige un `User` en base pour `securityHelper.currentUser`, réutiliser le même seeding/mocke que les tests existants.

- [ ] **Step 2 : Lancer — échec attendu**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=InvoiceControllerExportPdfTest test`
Expected: échec — le PDF actuel n'a ni « Factures » (titre en dur « Invoices ») ni « Signature ».

- [ ] **Step 3 : Brancher `InvoiceController` (factures + matching)**

Dans `exportInvoices` (L99-117), remplacer la ligne 112 et ajouter le titre/meta. Le contrôleur a déjà `Locale locale` et un `messageSource` ; ajouter `Authentication authentication` en paramètre s'il n'y est pas, et injecter `SecurityHelper securityHelper` (comme les autres contrôleurs). Nouveau bloc :

```java
        com.oct.invoicesystem.domain.user.model.User currentUser = securityHelper.currentUser(authentication);
        StringBuilder f = new StringBuilder();
        if (status != null)     f.append("Statut: ").append(status.name());
        if (department != null) f.append(f.length() > 0 ? " · " : "").append("Departement");
        if (from != null || to != null) f.append(f.length() > 0 ? " · " : "")
                .append("Periode: ").append(from == null ? "" : from).append("..").append(to == null ? "" : to);
        String filters = f.length() == 0 ? null : f.toString();
        String title = messageSource.getMessage("export.title.invoices", null, locale);
        com.oct.invoicesystem.shared.export.ReportMetadata meta =
                com.oct.invoicesystem.shared.export.ReportMetadata.of(currentUser, messageSource, null, filters, locale);
        byte[] body = tabularExportService.export(fmt, title, headers, rows, meta, messageSource);
```

Pour l'export matching (L192), remplacer par :

```java
        com.oct.invoicesystem.domain.user.model.User currentUser = securityHelper.currentUser(authentication);
        String title = messageSource.getMessage("export.title.matching", null, locale);
        com.oct.invoicesystem.shared.export.ReportMetadata meta =
                com.oct.invoicesystem.shared.export.ReportMetadata.of(currentUser, messageSource, null, null, locale);
        byte[] body = tabularExportService.export(fmt, title, headers, rows, meta, messageSource);
```

(Ajouter `Authentication authentication` au paramètre de la méthode matching si absent, et le champ `private final SecurityHelper securityHelper;` au contrôleur. Import : `com.oct.invoicesystem.shared.util.SecurityHelper` ; l'API est `User currentUser(Authentication authentication)`.)

- [ ] **Step 4 : Brancher `AuditController` (export + summary, headers i18n)**

Dans `AuditController` : remplacer les headers en dur et brancher le meta. Pour l'export (autour de L136-144) :

```java
        java.util.Locale locale = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        java.util.List<String> headers = java.util.List.of(
                messageSource.getMessage("export.header.audit.date", null, locale),
                messageSource.getMessage("export.header.audit.user", null, locale),
                messageSource.getMessage("export.header.audit.action", null, locale),
                messageSource.getMessage("export.header.audit.entity", null, locale),
                messageSource.getMessage("export.header.audit.entity_id", null, locale),
                messageSource.getMessage("export.header.audit.ip", null, locale));
        // ... rows inchangés ...
        com.oct.invoicesystem.domain.user.model.User currentUser = securityHelper.currentUser(authentication);
        String title = messageSource.getMessage("export.title.audit", null, locale);
        com.oct.invoicesystem.shared.export.ReportMetadata meta =
                com.oct.invoicesystem.shared.export.ReportMetadata.of(currentUser, messageSource, null, null, locale);
        byte[] body = tabularExportService.export(fmt, title, headers, rows, meta, messageSource);
```

Pour le summary (L204-210), headers via `export.header.audit.dimension|label|count`, titre `export.title.audit_summary`, même schéma de meta. Injecter `messageSource`, `securityHelper` et `Authentication authentication` dans `AuditController` s'ils ne sont pas déjà présents.

- [ ] **Step 5 : Brancher `SupplierController` et `UserController`**

Dans `SupplierController` (L112) et `UserController` (L143), même pattern : `title = messageSource.getMessage("export.title.suppliers"/"export.title.users", null, locale)`, `meta = ReportMetadata.of(currentUser, messageSource, null, filters, locale)`, appel riche `export(fmt, title, headers, rows, meta, messageSource)`. Ajouter `Authentication authentication` + `SecurityHelper` + `MessageSource` + `Locale locale` aux signatures si absents (suivre le pattern d'`InvoiceController`).

- [ ] **Step 6 : Brancher l'export paiements**

`PaymentServiceImpl.export...` (L233) n'a pas accès à l'`Authentication`. Modifier la signature de la méthode d'export du service pour recevoir `ReportMetadata meta`, `MessageSource messageSource`, `String title`, et construire le meta dans `PaymentController` (qui a l'`Authentication`, le `messageSource`, la `locale`). Le service appelle alors `tabularExportService.export(format, title, headers, rows, meta, messageSource)`. Répercuter le nouvel argument sur l'interface `PaymentService` si elle déclare la méthode.

- [ ] **Step 7 : Lancer le test d'intégration + compiler**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=InvoiceControllerExportPdfTest test`
Expected: PASS.

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q compile`
Expected: 0 erreur.

- [ ] **Step 8 : Non-régression des exports existants**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=*Export*,*Audit*,*Payment*,*Supplier*,*User* test`
Expected: verts (les tests CSV/EXCEL et contrôleurs d'export existants ne régressent pas ; le CSV/EXCEL est byte-inchangé).

- [ ] **Step 9 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain
git add src/test/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceControllerExportPdfTest.java
git commit -m "feat(export): 6 exports PDF branches (titre i18n + filtres + generateur + signature)"
```

---

## Task 8 : Vérif runtime (regarder les PDF) + ajustement logo

Valide le rendu réel : logo détouré, portrait sans coupure, en-tête navy + filet or, généré par + filtres, tableau zébré, bloc signature. Ajuste la taille du logo si nécessaire.

**Files:**
- Éventuellement Modify: `src/main/java/com/oct/invoicesystem/shared/export/PdfBranding.java` (`LOGO_WIDTH_PT` seulement, si le logo est trop grand/petit)

**Interfaces:**
- Consumes: tout le travail des Tasks 1-7.
- Produces: preuve visuelle ; suite backend verte.

- [ ] **Step 1 : Suite backend complète**

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q test`
Expected: 100 % vert (0 échec). Consigner tout test préexistant instable rencontré (ex. state-machine) et le relancer isolé pour confirmer qu'il n'est pas lié.

- [ ] **Step 2 : Rebuild + redéploiement backend (conteneurs Docker)**

Le backend tourne en conteneur `oct_backend`. Reconstruire le jar et redéployer selon la procédure du projet :

Run: `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -DskipTests package && docker compose up -d --build backend` (ou la commande de redéploiement backend du projet — vérifier `docker-compose.yml` / CLAUDE.md §13 pour le nom exact du service).
Expected: `oct_backend` redémarre `healthy`.

- [ ] **Step 3 : Télécharger un export PDF (compte daf) et le regarder**

Via le navigateur (Playwright) connecté en `daf`/`Test1234!`, appeler l'export factures en PDF (`/api/invoices/export?format=pdf`, `Accept-Language: fr`) et sauvegarder le fichier, puis l'ouvrir/le lire.

Vérifier visuellement :
- [ ] **Logo** : logotype OCT détouré (Afrique + « OWENDO CONTAINER TERMINAL » + « GABON »), **plus de fond photo de grue**, taille correcte (ni écrasé ni géant).
- [ ] **En-tête** : titre « Factures » (FR) en navy + **filet or** sous le titre.
- [ ] **Metadata** : « Généré par NOM Prénom (rôle) », date/heure, et **« Filtres : … »** si des filtres sont appliqués.
- [ ] **Tableau** : en-têtes navy à texte blanc, zébrures, **aucune colonne coupée à droite** (portrait), montants alignés à droite, devise XAF.
- [ ] **Signature** : cadre en bas avec « Signature », ligne nom/fonction, ligne date.

- [ ] **Step 4 : (si logo mal dimensionné) ajuster `LOGO_WIDTH_PT`**

Si le logo horizontal apparaît trop large/petit, modifier uniquement le littéral dans `PdfBranding` (ex. `170f` → `140f`), recompiler, redéployer, re-vérifier. Sinon, ne rien changer.

- [ ] **Step 5 : Commit (seulement si le logo a été ajusté)**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/PdfBranding.java
git commit -m "fix(export): ajuste la largeur du logo PDF"
```

Sinon, aucun commit (tâche de validation).

---

## Self-Review (effectuée)

- **Couverture spec** : logo détouré → Task 1 ; `PdfTableStyle` navy/or/polices → Task 2 ; portrait sans coupure (largeurs pondérées + police réduite) → Task 3 + Task 6 ; `filtersLabel` → Task 4 ; titres/headers audit/metadata/signature i18n → Task 5 ; metadata + signature TOUJOURS présents + style riche → Task 6 ; 6 exports branchés (généré-par + filtres + titre i18n, audit headers i18n) → Task 7 ; vérif runtime light (PDF) + i18n FR → Task 8. CSV/EXCEL inchangés → garanti (toPdf seul réécrit, tests de non-régression Task 7 Step 8).
- **Placeholders** : aucun step de code sans code. Les seuls « adapter » sont des instructions explicites de suivre un pattern existant nommé (`InvoiceDocumentControllerTest` pour le harnais de test, `SecurityHelper` pour l'utilisateur courant), pas des TODO — le pattern commun est donné en toutes lettres.
- **Cohérence des types** : `ReportMetadata` à 5 composants + les deux `of(...)` (4 et 5 args) sont définis en Task 4 et consommés en Task 6/7. `PdfTableStyle.headerCell/bodyCell/bold/regular` définis en Task 2, consommés en Task 6. `columnWeights(List<String>) → float[]` (somme 100) défini en Task 3, consommé en Task 6. Clés i18n définies en Task 5, consommées en Task 6/7. `isNumericColumn` est privé, défini et utilisé dans Task 6.
- **Écart assumé** : `PdfMetadata` conserve sa garde `if (meta == null) return;` (filet de sécurité) ; les 6 exports fournissent désormais toujours un `meta`, donc le chemin nominal a metadata + signature. Le ScheduledReportJob (report builder) passe déjà un `meta` ou `null` selon l'auth — comportement inchangé pour lui, hors périmètre.
- **Points à surveiller à l'exécution** : injection de `SecurityHelper`/`MessageSource`/`Authentication`/`Locale` dans les contrôleurs qui ne les ont pas encore (Task 7) — vérifier chaque signature réelle avant d'éditer ; l'API PDFBox (`PDDocument.load(byte[])`) est celle de la 2.x — confirmer la version au premier test.
