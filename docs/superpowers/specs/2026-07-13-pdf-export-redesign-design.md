# Refonte des PDF d'export de liste — Design

**Date :** 2026-07-13
**Statut :** validé (brainstorming)
**Périmètre :** backend Java (Spring Boot + iText 8), i18n. Aucun changement front.

## Problème

Les PDF d'**export de liste** (factures, audit, fournisseurs, utilisateurs, paiements, rapprochement) sont générés par `TabularExportService.toPdf` et sont visuellement pauvres :

- **Tableau qui déborde** : 11 colonnes en portrait avec largeurs toutes égales (`widths[i] = 1f`) → la dernière colonne (« Issue Date ») est **coupée** hors de la page. Constaté sur un vrai `invoices.pdf`.
- **Aucun en-tête metadata** (généré par / date / période) et **aucun bloc signature** : ces blocs ne sont rendus que si `meta != null && messageSource != null`, or **tous les appelants d'export de liste passent `meta = null`** (seul le Report Builder passe `meta`).
- **Police par défaut** iText (Helvetica non déclarée), pas de couleur, pas de zébrure, pas de hiérarchie.
- **Titre en dur en anglais** (`"Invoices"`, `"Audit"`, `"Payments"`…) ; headers d'audit en dur (`"Date", "User ID", …`).
- **Mauvais logo** : le fichier `branding/oct-logo.png` embarqué porte un **fond photo** (grue en filigrane) au lieu du logotype OCT détouré. Le bon asset (`assets/logos/oct-logo.png`) existe déjà, détouré sur fond transparent.

En regard, `InvoicePdfService` (facture individuelle « Bon À Payer », soignée en PR #3) est riche : couleurs `OCT_NAVY`/`OCT_GOLD`, polices Helvetica bold/regular explicites, en-tête logo + filet or, tableaux navy à en-têtes blancs + zébrures + largeurs adaptées + alignement des montants, footer. **Les deux chemins de génération PDF sont disjoints** ; le soin de PR #3 n'a jamais touché les exports tabulaires.

> Note sur l'« anglais » : les **headers factures** et le **statut** sont déjà i18n via `messageSource` + `locale`. Le PDF apparaît en anglais quand la requête porte `Accept-Language: en` (cas du test). Le vrai défaut i18n est le **titre en dur** (tous les exports) et les **headers d'audit en dur**.

## Objectif

Porter les 6 PDF d'export de liste au niveau visuel d'`InvoicePdfService`, avec **en-tête metadata + bloc e-signature systématiques**, **portrait sans coupure** (police réduite + largeurs pondérées), et **localisation** (titres + audit).

## Décisions (brainstorming)

1. **Orientation** : rester en **portrait**, réduire la police et pondérer les largeurs pour que tout tienne (pas de paysage).
2. **Colonnes** : **toutes conservées** ; police réduite (~7–8 pt) + largeurs pondérées par type de colonne + wrap dans les cellules. Aucune configuration par export, aucune donnée perdue.
3. **Infos obligatoires sur chaque PDF** : **Généré par (NOM Prénom + rôle)** ; **Période / filtres appliqués** ; **Bloc e-signature (cadre + date + nom)**. La **date/heure de génération** (fuseau Libreville) est incluse dans le bloc « généré par » (traçabilité standard, comme `InvoicePdfService`).
4. **Étendue** : brancher **les 6 exports de liste** maintenant (facture, audit, fournisseur, utilisateur, paiement, rapprochement).
5. **Langue** : **suivre la locale de l'utilisateur** (FR par défaut) pour titre, en-têtes et valeurs traduisibles.

## Architecture

Cinq unités, chacune avec une responsabilité claire.

### 0. Logo PDF (correction d'asset)
Le logo embarqué dans les PDF (`src/main/resources/branding/oct-logo.png`, chargé par `PdfBranding`) est **le mauvais fichier** : il porte un **fond photographique** (grue de terminal en filigrane, bordure bleu clair) au lieu d'un logo détouré. Le bon logo est **`assets/logos/oct-logo.png`** : logotype OCT propre, **fond transparent** (carte d'Afrique en dégradé bleu + « OWENDO CONTAINER TERMINAL » + « GABON » + filet or).
- **Action** : remplacer le contenu de `src/main/resources/branding/oct-logo.png` par celui de `assets/logos/oct-logo.png` (même chemin classpath → aucun changement de code dans `PdfBranding`).
- Ce logo étant plus large que haut (bandeau horizontal), vérifier que `LOGO_WIDTH_PT = 170f` dans `PdfBranding` reste visuellement correct ; ajuster la largeur si le rendu est trop grand/petit (seul ce littéral peut changer, pas la logique).
- S'applique à **tous** les PDF qui passent par `PdfBranding.addLetterhead` (exports de liste **et**, mécaniquement, tout futur appelant). `InvoicePdfService` **n'utilise pas** `PdfBranding` (il dessine son propre en-tête textuel « OCT ») → hors périmètre, non touché.

### 1. `PdfTableStyle` (nouveau, `shared/export/`)
Centralise le style de tableau extrait d'`InvoicePdfService` pour qu'export de liste et facture partagent la même identité visuelle.
- Constantes couleur : `OCT_NAVY` (15,37,64), `OCT_GOLD` (200,168,75), `ROW_ALT` (243,244,246), `LIGHT_GRAY` (248,249,250), blanc.
- Fabrique de polices : `HELVETICA_BOLD` / `HELVETICA`.
- Méthodes : `headerCell(text, font)` (fond navy, texte blanc, padding), `bodyCell(text, font, size, altBg, align)` (zébrure + bordure fine + alignement).
- **Ce qu'il ne fait pas** : il ne connaît ni les données ni la mise en page globale — il ne style que des cellules.

### 2. Largeurs de colonnes pondérées (dans `TabularExportService`)
Fonction pure `columnWeights(headers)` qui attribue un poids par colonne d'après un heuristique de nom d'en-tête (indices i18n-agnostiques via mots-clés multilingues ou position) :
- Étroit (poids 1) : référence, montant, devise, statut, date, code département, quantité.
- Large (poids 2–3) : libellé, description, nom fournisseur, email.
- Repli : si aucun mot-clé ne matche, poids 1.
Somme normalisée à 100 % → `UnitValue.createPercentArray`. **Testable indépendamment** (entrée = liste de headers, sortie = float[] sommant à 100).

### 3. Refonte de `TabularExportService.toPdf`
Signature inchangée. Nouveau rendu :
- `Document` A4 portrait, marges 36.
- En-tête : logo OCT (`PdfBranding`) + **titre** (déjà i18n par l'appelant) en navy gras + **filet or 1.5 pt** (`LineSeparator`).
- **Bloc metadata TOUJOURS rendu** via `PdfMetadata.renderHeader`. Règle de nullité clarifiée : dans le nouveau design, **les 6 exports fournissent toujours un `meta`** (via `ExportMetaFactory`, §5), donc le chemin nominal a toujours metadata + signature. La garde historique `if (meta == null) return;` dans `PdfMetadata` est **conservée comme filet de sécurité** (un appelant tiers sans meta ne plante pas — il obtient l'ancien layout sans metadata/signature), mais ce cas ne se produit plus pour les 6 exports visés. Contenu : généré par NOM+rôle, date/heure, **période/filtres** (`meta.filtersLabel()`).
- Tableau : police 7–8 pt, `columnWeights`, en-têtes navy (`PdfTableStyle.headerCell`), corps zébré (`PdfTableStyle.bodyCell`), montants/dates alignés selon type de colonne, wrap activé.
- **Bloc e-signature TOUJOURS rendu** via `PdfMetadata.renderSignatureBlock` : cadre bordé, libellé « Signature », **ligne nom/fonction**, ligne date.
- Footer discret (titre export + date + mention « usage interne »).
- CSV et EXCEL **inchangés**.

### 4. `ReportMetadata` enrichi
Ajout d'un champ `filtersLabel` (String nullable) = résumé lisible « Période : du … au … · Statut : … · Département : … ». Les champs existants (generatorName, generatorRole, generatedAt, periodLabel) sont conservés. `periodLabel` et `filtersLabel` peuvent coexister (période centrée + filtres sous le « généré par »).

### 5. Construction du `meta` + branchement des 6 exports
Un helper commun `ExportMetaFactory.forCurrentUser(messageSource, locale, filtersLabel)` :
- Lit l'utilisateur courant depuis le `SecurityContext` (NOM Prénom + rôle principal, rôle localisé via `messageSource`).
- Renseigne `generatedAt = Instant.now()`.
- Reçoit `filtersLabel` construit par l'appelant à partir de ses `@RequestParam` (période, statut, département…).
Chaque contrôleur/service d'export :
- construit un **titre i18n** (nouvelle clé `export.title.<domaine>`),
- construit `filtersLabel` à partir de ses filtres,
- appelle `export(fmt, titre, headers, rows, meta, messageSource)`.
- **Audit** : ses headers en dur (`"Date","User ID",…` et `"Dimension","Libelle","Nombre"`) passent en i18n (`export.header.audit.*`).

## i18n (clés nouvelles)

- Titres : `export.title.invoices`, `export.title.audit`, `export.title.audit_summary`, `export.title.matching`, `export.title.payments`, `export.title.suppliers`, `export.title.users`.
- Headers audit : `export.header.audit.date`, `.user`, `.action`, `.entity`, `.entity_id`, `.ip` ; summary : `export.header.audit.dimension`, `.label`, `.count`.
- Metadata / signature : `export.pdf.generated_by` (params : nom, rôle), `export.pdf.generated_at` (param : date), `export.pdf.filters` (param : résumé), `export.pdf.signature`, `export.pdf.signature.name`, `export.pdf.signature.date`, `export.pdf.footer`.
- Rôles : réutiliser les clés de rôle existantes si présentes, sinon `export.role.<ROLE>`.

**Encodage** : `messages_fr.properties` est **ISO-8859-1** (Latin-1). Toute addition FR doit être convertie (iconv) — pas d'em-dash ni de guillemets courbes ; ASCII `\uXXXX` pour les accents si nécessaire. (Règle projet connue.)

## Tests

Unitaires (`TabularExportServiceTest`, `PdfTableStyleTest`) :
- `toPdf` renvoie un PDF valide (magic `%PDF`).
- `columnWeights(headers)` : somme = 100 (±ε) ; une colonne « email »/« description » reçoit un poids > une colonne « montant »/« date » ; repli à poids égal si headers inconnus.
- Le PDF contient le bloc « généré par » et le bloc signature (extraction texte PDFBox : présence des libellés metadata + « Signature »).
- Titre i18n : un export en `Locale.FRENCH` produit le titre FR, en `Locale.ENGLISH` le titre EN (extraction texte).
- CSV/EXCEL **inchangés** : tests existants restent verts (non-régression byte-compatible du chemin CSV/EXCEL).

Intégration légère (au moins un contrôleur, ex. `InvoiceController`) : `GET /export?format=pdf` avec `Accept-Language: fr` → 200, `application/pdf`, corps commençant par `%PDF`, contenant le titre FR + « Signature ».

Vérif runtime finale : re-télécharger l'export factures (compte daf) et **regarder le PDF** (portrait sans coupure, **logo OCT détouré correct** — plus de fond photo, en-tête navy + filet or, généré par + filtres, tableau zébré, bloc signature).

## Hors périmètre

- `InvoicePdfService` (facture unique) et `RemittanceAdviceService` (avis de remise) : déjà soignés, **non touchés**.
- Report Builder : passe déjà `meta` ; bénéficie automatiquement du nouveau rendu, mais son branchement n'est pas retouché au-delà du champ `filtersLabel` optionnel.
- Pas de mode paysage, pas de sélection de colonnes par export, pas de refonte CSV/EXCEL.
- Défauts pré-existants sans rapport (aria des boutons, flaky test) hors sujet.

## Risques / points d'attention

- **iText 8** : `setFont` doit être appliqué par cellule/paragraphe (pas de font global fiable) — suivre le pattern d'`InvoicePdfService`.
- **`filtersLabel`** doit rester court (une ligne) pour ne pas casser la mise en page ; tronquer si trop long.
- **SecurityContext** en contexte d'export asynchrone/planifié (`ScheduledReportJob`) : peut ne pas avoir d'utilisateur → `ExportMetaFactory` doit tolérer l'absence (meta « système »).
- Encodage `messages_fr` (voir §i18n).
