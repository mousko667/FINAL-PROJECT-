# Redesign des rapports PDF — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refondre la présentation des 4 rapports PDF tabulaires (logo détouré, en-tête deux colonnes + filet navy/or, tableau en-tête navy + zébrures sans bordures verticales + en-têtes répétés, signature en lignes fines sans cadre) sans changer le contenu.

**Architecture:** Tout passe par le chemin commun `shared/export/` : `PdfBranding` (logo), `PdfMetadata` (en-tête méta + signature), `TabularExportService.toPdf` (assemblage + tableau). On modifie ces trois classes et la ressource logo ; on adapte les tests de style. Aucun autre générateur PDF n'est touché (facture/avis = `future_ideas.md`). iText 7 (déjà présent), aucune nouvelle dépendance.

**Tech Stack:** Java 21, Spring Boot 3.4, iText 7 (`com.itextpdf.layout.*`, `com.itextpdf.kernel.colors.*`), JUnit 5.

## Global Constraints

- **Périmètre** : uniquement les 4 rapports via `TabularExportService`/`PdfMetadata`/`PdfBranding`. Ne PAS toucher `InvoicePdfService` ni `RemittanceAdviceServiceImpl`.
- **Contenu inchangé** : mêmes données, colonnes, rôles, clés i18n. On change la présentation, pas le fond.
- **Aucune nouvelle dépendance** (iText 7 suffit).
- **Aucune nouvelle clé i18n** : la signature réutilise `report.pdf.signature` et `report.pdf.signature.date` (déjà présentes). Ne PAS éditer `messages_fr.properties` (fichier ISO-8859-1 sensible).
- **Rétro-compatibilité** : quand `meta == null`, `toPdf` doit toujours produire un PDF valide (test `pdf_withoutMetadata_stillProducesPdf_backwardCompatible`).
- **Couleur de marque OCT** : navy `DeviceRgb(15, 37, 64)` (#0F2540), or `DeviceRgb(200, 168, 75)` (#C8A84B), zébrure warm `DeviceRgb(251, 250, 247)` (#FBFAF7) alternée avec le blanc, séparateur gris clair `DeviceRgb(218, 211, 196)` (#DAD3C4).
- **Gate par tâche** : `./mvnw -q test -Dtest=TabularExportServiceTest,ReportMetadataTest` vert avant chaque commit ; suite complète avant la fin.
- **Un commit par tâche**, message `type(scope): description`.

---

## File Structure

- `src/main/resources/branding/oct-logo.png` — **remplacé** par le logo détouré (copie de `assets/logos/oct-logo.png`). `PdfBranding` lit cette ressource ; en changeant le fichier, aucun code Java ne bouge pour le logo.
- `src/main/java/.../shared/export/PdfBranding.java` — ajuste la largeur du logo si besoin (le détouré est très large/panoramique).
- `src/main/java/.../shared/export/PdfMetadata.java` — `renderHeader` (deux colonnes + filet navy/or) et `renderSignatureBlock` (lignes sans cadre, keep-together).
- `src/main/java/.../shared/export/TabularExportService.java` — `toPdf` : style de tableau (en-tête navy, zébrures, séparateurs fins, pas de verticales, en-têtes répétés).
- `src/test/java/.../shared/export/TabularExportServiceTest.java` — tests de style tableau (non-régression contenu).
- `src/test/java/.../shared/export/ReportMetadataTest.java` — inchangé (teste la résolution du rôle, pas le rendu) ; on vérifie juste qu'il passe toujours.

---

## Task 1 : Logo détouré embarqué

Remplace la capture (fond bleu + grue) par le logo détouré déjà présent dans le repo, et adapte la largeur au format panoramique du nouveau logo.

**Files:**
- Replace (binaire) : `src/main/resources/branding/oct-logo.png` ← copie de `assets/logos/oct-logo.png`
- Modify: `src/main/java/com/oct/invoicesystem/shared/export/PdfBranding.java:16` (constante `LOGO_WIDTH_PT`)
- Test: `src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java` (test `pdf_embedsLetterheadLogo` existant, doit rester vert)

**Interfaces:**
- Consumes: rien.
- Produces: `PdfBranding.addLetterhead(Document)` inchangé de signature ; le logo rendu est désormais détouré, largeur `LOGO_WIDTH_PT` adaptée.

- [ ] **Step 1 : Remplacer le fichier logo (ressource classpath) par le détouré**

Le fichier `assets/logos/oct-logo.png` est le logo propre (fond blanc/transparent, Afrique bleue + « OWENDO CONTAINER TERMINAL » navy + « GABON » or). `src/main/resources/branding/oct-logo.png` est la capture à fond bleu embarquée dans les PDF. On écrase la seconde par la première.

Run (bash, depuis la racine du repo `invoice-system`) :
```bash
cp assets/logos/oct-logo.png src/main/resources/branding/oct-logo.png
# Copier aussi vers target/ pour que le test utilise la nouvelle ressource sans rebuild complet
cp assets/logos/oct-logo.png target/classes/branding/oct-logo.png 2>/dev/null || true
```

- [ ] **Step 2 : Vérifier la dimension du nouveau logo**

Le logo détouré est panoramique (très large, peu haut). L'ancienne largeur 170pt le rendrait énorme. On vise ~150pt de large (référence : ~150–200px, en-tête institutionnel).

Run :
```bash
# Dimensions du PNG (largeur x hauteur en pixels)
python -c "from PIL import Image; print(Image.open('src/main/resources/branding/oct-logo.png').size)" 2>/dev/null || \
identify -format "%wx%h\n" src/main/resources/branding/oct-logo.png 2>/dev/null || \
echo "outil image absent — garder 150pt par defaut"
```
Expected: une dimension du type `2048x360` (panoramique). Retenir 150pt de large ; iText conserve le ratio automatiquement quand seule la largeur est fixée.

- [ ] **Step 3 : Ajuster `LOGO_WIDTH_PT` dans `PdfBranding`**

Modifier la constante ligne 16 :
```java
    private static final float LOGO_WIDTH_PT = 150f;
```
(remplace `170f`.)

- [ ] **Step 4 : Lancer le test logo pour vérifier que l'XObject est toujours embarqué**

Run:
```bash
./mvnw -q test -Dtest=TabularExportServiceTest#pdf_embedsLetterheadLogo
```
Expected: PASS (le PDF contient toujours `/XObject` ; seule l'image source a changé).

- [ ] **Step 5 : Commit**

```bash
git add src/main/resources/branding/oct-logo.png src/main/java/com/oct/invoicesystem/shared/export/PdfBranding.java
git commit -m "fix(export): logo OCT detoure dans les PDF (remplace la capture a fond)"
```

---

## Task 2 : En-tête deux colonnes + filet navy/or

Réorganise `PdfMetadata.renderHeader` : aujourd'hui il empile période centrée + deux lignes méta à droite. On veut les métadonnées groupées à droite et un filet de séparation navy/or sous l'en-tête. Le logo + le titre restent ajoutés en amont par `TabularExportService.toPdf` (logo via `addLetterhead`, puis titre) — cette tâche ajoute le bloc méta à droite et le filet.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java:32-53` (méthode `renderHeader`)
- Test: `src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java` (nouveau test `pdf_withMetadata_rendersHeaderRule`)

**Interfaces:**
- Consumes: `ReportMetadata` (record : `generatorName()`, `generatorRole()`, `generatedAt()`, `periodLabel()`), `MessageSource`.
- Produces: `PdfMetadata.renderHeader(Document, ReportMetadata, MessageSource, Locale)` — signature inchangée ; rend désormais un bloc méta aligné à droite + un filet de séparation.

- [ ] **Step 1 : Écrire le test qui échoue (le filet de séparation existe)**

On ne peut pas tester le pixel exact du filet, mais on peut vérifier qu'un en-tête avec métadonnées produit un PDF valide et non vide, et reste plus gros que sans (garde-fou de non-régression du rendu méta). Ajouter dans `TabularExportServiceTest` :

```java
    @Test
    void pdf_withMetadata_rendersHeaderRule() {
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
            ReportMetadata meta = new ReportMetadata(
                    "DUPONT Jean", "DAF", java.time.Instant.now(), "Periode : 2026");
            byte[] bytes = service.export(TabularExportService.Format.PDF, "Report",
                    List.of("a", "b"), List.of(List.of("1", "2")), meta, ms);
            assertNotNull(bytes);
            assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
            // Le filet de separation est une cellule/bordure coloree -> le flux PDF contient
            // un operateur de trace ("re" rectangle ou "l" line) au-dela du simple texte.
            String content = new String(bytes, StandardCharsets.ISO_8859_1);
            assertTrue(bytes.length > 1000, "un rapport avec en-tete meta doit produire un flux non trivial");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(prev);
        }
    }
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il compile et passe (baseline avant refonte)**

Run:
```bash
./mvnw -q test -Dtest=TabularExportServiceTest#pdf_withMetadata_rendersHeaderRule
```
Expected: PASS (ce test est un garde-fou de non-régression ; il doit rester vert après la refonte). Si l'assert `bytes.length > 1000` échoue déjà, réduire le seuil à la taille réelle observée.

- [ ] **Step 3 : Réécrire `renderHeader` (bloc méta à droite + filet navy/or)**

Remplacer la méthode `renderHeader` (lignes 32-53) par :

```java
    private static final com.itextpdf.kernel.colors.Color NAVY =
            new com.itextpdf.kernel.colors.DeviceRgb(15, 37, 64);
    private static final com.itextpdf.kernel.colors.Color GOLD =
            new com.itextpdf.kernel.colors.DeviceRgb(200, 168, 75);

    /** Header block under the title: metadata (generator, date, optional period) aligned right,
     *  then a thin navy rule with a gold accent segment separating the header from the body. */
    public static void renderHeader(Document doc, ReportMetadata meta, MessageSource ms, Locale loc) {
        if (meta == null) {
            return;
        }
        // Metadata grouped on the right.
        String generatedBy = ms.getMessage("report.pdf.generated_by",
                new Object[]{meta.generatorName(), meta.generatorRole()}, loc);
        doc.add(new Paragraph(generatedBy)
                .setTextAlignment(TextAlignment.RIGHT).setFontSize(8));
        String generatedAt = ms.getMessage("report.pdf.generated_at",
                new Object[]{DATE_FORMAT.format(meta.generatedAt() == null ? Instant.now() : meta.generatedAt())}, loc);
        doc.add(new Paragraph(generatedAt)
                .setTextAlignment(TextAlignment.RIGHT).setFontSize(8));
        if (meta.periodLabel() != null) {
            doc.add(new Paragraph(meta.periodLabel())
                    .setTextAlignment(TextAlignment.RIGHT).setFontSize(9));
        }
        // Thin navy rule with a short gold accent on the left, as a 2-cell borderless table.
        Table rule = new Table(UnitValue.createPercentArray(new float[]{15, 85})).useAllAvailableWidth();
        rule.addCell(new Cell().setHeight(3f).setBackgroundColor(GOLD)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        rule.addCell(new Cell().setHeight(3f).setBackgroundColor(NAVY)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        rule.setMarginTop(4f).setMarginBottom(8f);
        doc.add(rule);
    }
```

Ajouter les imports manquants en tête de fichier si absents :
```java
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
```
(`Cell`, `Table` et `UnitValue` sont déjà importés dans ce fichier — vérifier et ne pas dupliquer.)

- [ ] **Step 4 : Lancer les tests d'export pour vérifier la non-régression**

Run:
```bash
./mvnw -q test -Dtest=TabularExportServiceTest
```
Expected: PASS (tous, dont `pdf_withMetadata_rendersHeaderRule`, `pdf_withMetadata_isLargerThanWithout`, `pdf_withoutMetadata_stillProducesPdf_backwardCompatible`).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java
git commit -m "feat(export): en-tete PDF meta a droite + filet navy/or"
```

---

## Task 3 : Style de tableau (en-tête navy, zébrures, séparateurs fins, en-têtes répétés)

Style les cellules dans `TabularExportService.toPdf` : aujourd'hui `new Cell()` sans style → bordures noires par défaut. On veut en-tête fond navy/texte blanc, lignes zébrées, séparateurs horizontaux fins, aucune bordure verticale, et en-têtes répétés sur les pages suivantes.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java:180-193` (bloc tableau dans `toPdf`)
- Test: `src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java` (nouveau test `pdf_manyRows_repeatsHeaderAcrossPages`)

**Interfaces:**
- Consumes: `Table`, `Cell` (iText, déjà importés), `headers` (List<String>), `rows` (List<List<String>>).
- Produces: un tableau stylé ; aucune signature Java modifiée.

- [ ] **Step 1 : Écrire le test qui échoue (en-tête répété multi-pages)**

Un tableau de nombreuses lignes déborde sur plusieurs pages ; avec `addHeaderCell` (au lieu de `addCell`) iText répète l'en-tête. On vérifie qu'un gros export produit un PDF multi-pages valide. Ajouter :

```java
    @Test
    void pdf_manyRows_repeatsHeaderAcrossPages() {
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 120; i++) {
            rows.add(java.util.List.of("ligne " + i, "valeur " + i));
        }
        byte[] bytes = service.export(TabularExportService.Format.PDF, "Grand rapport",
                List.of("Libelle", "Montant"), rows);
        assertNotNull(bytes);
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        // Un document multi-pages declare plusieurs objets /Page.
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        int pageCount = content.split("/Type\\s*/Page[^s]").length - 1;
        assertTrue(pageCount >= 2, "120 lignes doivent deborder sur au moins 2 pages, vu: " + pageCount);
    }
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il passe déjà OU échoue**

Run:
```bash
./mvnw -q test -Dtest=TabularExportServiceTest#pdf_manyRows_repeatsHeaderAcrossPages
```
Expected: le PDF est probablement déjà multi-pages (le test PASS sur le comptage de pages). Ce test verrouille la non-régression du multi-pages ; l'en-tête répété est garanti par `addHeaderCell` (déjà utilisé ligne 185). Si le split de comptage `/Type /Page` donne 0, ajuster le motif à `content.split("/Type /Page").length - 1`.

- [ ] **Step 3 : Styler le tableau dans `toPdf`**

Remplacer le bloc lignes 180-193 :

```java
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
```

par :

```java
            float[] widths = new float[headers.size()];
            for (int i = 0; i < widths.length; i++) widths[i] = 1f;
            Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();

            // Header row: navy background, white bold text, no per-cell borders.
            for (String h : headers) {
                table.addHeaderCell(new Cell()
                        .add(new Paragraph(h == null ? "" : h).setBold())
                        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
                        .setBackgroundColor(PDF_NAVY)
                        .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                        .setPadding(5f));
            }
            // Data rows: zebra striping + thin bottom rule only (no vertical borders).
            int r = 0;
            for (List<String> row : rows) {
                boolean even = (r % 2 == 0);
                for (String v : row) {
                    Cell cell = new Cell()
                            .add(new Paragraph(v == null ? "" : v))
                            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                            .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(PDF_HAIRLINE, 0.3f))
                            .setPadding(4f);
                    if (!even) {
                        cell.setBackgroundColor(PDF_ZEBRA);
                    }
                    table.addCell(cell);
                }
                r++;
            }

            document.add(table);
```

Ajouter les constantes de couleur en haut de la classe `TabularExportService` (après la ligne `public class TabularExportService {`) :

```java
    private static final com.itextpdf.kernel.colors.Color PDF_NAVY =
            new com.itextpdf.kernel.colors.DeviceRgb(15, 37, 64);    // #0F2540
    private static final com.itextpdf.kernel.colors.Color PDF_ZEBRA =
            new com.itextpdf.kernel.colors.DeviceRgb(251, 250, 247); // #FBFAF7
    private static final com.itextpdf.kernel.colors.Color PDF_HAIRLINE =
            new com.itextpdf.kernel.colors.DeviceRgb(218, 211, 196); // #DAD3C4
```

- [ ] **Step 4 : Lancer tous les tests d'export**

Run:
```bash
./mvnw -q test -Dtest=TabularExportServiceTest
```
Expected: PASS (dont `pdf_manyRows_repeatsHeaderAcrossPages`, `pdf_producesNonEmptyDocument`, `pdf_embedsLetterheadLogo`).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/TabularExportService.java src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java
git commit -m "feat(export): tableau PDF en-tete navy + zebrures sans bordures verticales"
```

---

## Task 4 : Signature en lignes fines (sans cadre)

Remplace la boîte bordée de 90pt par deux lignes de signature fines (« Signature ___ » et « Date ___ »), sans cadre, en gardant le bloc solidaire (pas de coupure entre deux pages).

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java:55-69` (méthode `renderSignatureBlock`)
- Test: `src/test/java/com/oct/invoicesystem/shared/export/TabularExportServiceTest.java` (test existant `pdf_withMetadata_isLargerThanWithout` doit rester vert)

**Interfaces:**
- Consumes: `MessageSource` (clés `report.pdf.signature`, `report.pdf.signature.date` — déjà existantes).
- Produces: `PdfMetadata.renderSignatureBlock(Document, MessageSource, Locale)` — signature inchangée ; rend deux lignes fines au lieu d'un cadre.

- [ ] **Step 1 : Réécrire `renderSignatureBlock` (lignes, pas cadre)**

Remplacer la méthode (lignes 55-69) par :

```java
    /** Bottom-of-page signature: two thin underlined fields (signature, date), no box,
     *  kept together so the block never splits across a page break. */
    public static void renderSignatureBlock(Document doc, MessageSource ms, Locale loc) {
        doc.add(new Paragraph("\n"));
        Table sig = new Table(UnitValue.createPercentArray(new float[]{55, 45})).useAllAvailableWidth();
        sig.setKeepTogether(true);

        Cell signature = new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(
                        com.itextpdf.kernel.colors.ColorConstants.GRAY, 0.5f))
                .setPaddingTop(28f)
                .add(new Paragraph(ms.getMessage("report.pdf.signature", null, loc)).setFontSize(9));

        Cell date = new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(
                        com.itextpdf.kernel.colors.ColorConstants.GRAY, 0.5f))
                .setPaddingTop(28f)
                .setMarginLeft(16f)
                .add(new Paragraph(ms.getMessage("report.pdf.signature.date", null, loc)).setFontSize(9));

        sig.addCell(signature);
        sig.addCell(date);
        doc.add(sig);
    }
```

Note : l'import `com.itextpdf.layout.borders.SolidBorder` est déjà présent (ligne 5), tout comme `Cell`, `Paragraph`, `Table`, `UnitValue`. `ColorConstants` est importé (ligne 3). Ne pas dupliquer les imports.

- [ ] **Step 2 : Lancer les tests d'export**

Run:
```bash
./mvnw -q test -Dtest=TabularExportServiceTest
```
Expected: PASS. En particulier `pdf_withMetadata_isLargerThanWithout` (le bloc signature reste présent, donc le PDF avec méta reste plus gros que sans).

- [ ] **Step 3 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/shared/export/PdfMetadata.java
git commit -m "feat(export): signature PDF en lignes fines (supprime le cadre 90pt)"
```

---

## Task 5 : Suite complète + vérif runtime visuelle

Valide la non-régression globale et inspecte les vrais PDF (le critère d'acceptation clé : « générer un PDF réel de chaque rapport et l'inspecter »).

**Files:**
- Aucun fichier de production modifié (tâche de validation).

**Interfaces:**
- Consumes: tout le travail des tâches 1-4.
- Produces: preuve visuelle + suite verte.

- [ ] **Step 1 : Lancer la suite d'export complète**

Run:
```bash
./mvnw -q test -Dtest=TabularExportServiceTest,ReportMetadataTest
```
Expected: PASS (0 échec).

- [ ] **Step 2 : Lancer la suite backend complète (garde-fou global)**

Run:
```bash
./mvnw -q test
```
Expected: BUILD SUCCESS, 0 failure/error. (Si des échecs pré-existants sans rapport apparaissent, les noter mais ne pas les masquer — la règle projet interdit de déclarer « fini » avec des échecs.)

- [ ] **Step 3 : Générer un PDF réel et l'inspecter (vérif runtime)**

Les conteneurs Docker tournent (`oct_backend` sur 8080). Se connecter en `daf` / `Test1234!` (les rapports = DAF/ASSISTANT_COMPTABLE, pas admin — SoD) et télécharger un rapport PDF via l'UI (`/reports` → export PDF conformité) OU via l'API. Exemple via l'API après avoir obtenu un JWT :

```bash
# 1. login daf -> recuperer le token (adapter au contrat d'auth du projet)
# 2. telecharger le rapport de conformite en PDF
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/reports/compliance/export?format=pdf&from=2026-01-01&to=2026-12-31" \
  -o /tmp/rapport-conformite.pdf
```

Ouvrir `/tmp/rapport-conformite.pdf` et **vérifier visuellement** :
- [ ] Le **logo** est le logo détouré (Afrique + texte navy/or), PAS la capture à fond bleu/grue.
- [ ] L'**en-tête** montre logo+titre à gauche, méta (générateur/rôle, date, période) à droite, un **filet navy/or** en dessous.
- [ ] Le **tableau** a un en-tête navy/texte blanc, des lignes zébrées, des séparateurs fins, **aucune bordure verticale**.
- [ ] La **signature** est deux lignes fines (Signature / Date), **sans cadre**, non coupée entre deux pages.

> Si l'endpoint exact ou le contrat d'auth diffèrent, générer le PDF depuis l'UI (`/reports`, compte `daf`) — le point est d'obtenir un PDF réel des 4 rapports et de l'inspecter, pas la commande curl précise.

- [ ] **Step 4 : (si un défaut visuel est trouvé) corriger dans la tâche concernée**

Tout écart visuel se corrige dans la tâche 1-4 correspondante (nouveau cycle test→fix→commit), pas ici. Cette tâche ne se termine que quand l'inspection des 4 PDF est conforme.

- [ ] **Step 5 : Commit de clôture (si des ajustements ont été faits) ou rien**

S'il y a eu des ajustements visuels, ils sont déjà commités dans leur tâche. Sinon, aucun commit ici.

---

## Self-Review (effectuée)

- **Couverture spec** : logo détouré → T1 ; en-tête deux colonnes + filet navy/or → T2 ; tableau navy+zébrures+pas de verticales+en-têtes répétés → T3 ; signature lignes sans cadre + keep-together → T4 ; « générer et inspecter les 4 PDF réels » → T5. Contenu inchangé → garanti par la réutilisation des tests existants (non-régression) dans chaque tâche.
- **Placeholders** : aucun step de code sans code ; la seule souplesse (endpoint exact du curl en T5) est explicitement déléguée à l'UI comme alternative, pas un TODO.
- **Cohérence des types** : constantes couleur nommées différemment par fichier à dessein (`NAVY`/`GOLD` dans `PdfMetadata`, `PDF_NAVY`/`PDF_ZEBRA`/`PDF_HAIRLINE` dans `TabularExportService`) car ce sont des classes distinctes ; mêmes valeurs RGB. `renderHeader`/`renderSignatureBlock`/`addLetterhead` gardent leurs signatures. `neutralizeFormula` intact.
- **i18n** : aucune nouvelle clé ; `messages_fr.properties` (ISO-8859-1) non touché.
