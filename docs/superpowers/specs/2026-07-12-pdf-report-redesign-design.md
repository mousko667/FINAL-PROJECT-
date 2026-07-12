# Redesign des rapports PDF exportables

**Date :** 2026-07-12
**Branche :** `feat/pdf-report-redesign` (depuis `main`)
**Statut :** Spec validée en brainstorming, prête pour writing-plans
**Spec sœur :** [2026-07-12-ui-color-enrichment-design.md](2026-07-12-ui-color-enrichment-design.md)
(enrichissement couleur UI — sujet distinct, front). Cette spec-ci est **backend / PDF**.
**Skills mobilisés :** `frontend-design` (direction visuelle appliquée au document).

---

## 1. Contexte et problème

Les rapports PDF exportables sont générés côté backend (Java, iText) par le chemin commun
`shared/export/` : `TabularExportService` (assemble le document), `PdfBranding` (logo),
`PdfMetadata` (en-tête méta + bloc signature). Quatre rapports l'empruntent (audit,
conformité, synthèse exécutive, builder de rapports custom).

Le commanditaire (2026-07-12) juge le rendu actuel « pas fameux » et cite **trois défauts
précis**, tous confirmés dans le code :

1. **Logo = une capture, pas le logo.** `PdfBranding` embarque `/branding/oct-logo.png`,
   qui est une **capture d'écran** du logo posée sur un **fond dégradé bleu avec une grue
   en filigrane**. Insérée telle quelle, elle colle tout le rectangle avec son fond → « au
   lieu d'extraire le logo, tu as inséré la photo ».
2. **Tableau trop gras.** `TabularExportService.toPdf` n'applique **aucun** style aux
   cellules → bordures iText **par défaut** (0.5pt noires sur toutes les cellules).
3. **Cadre de signature trop grand.** `PdfMetadata.renderSignatureBlock` dessine une boîte
   bordée grise de **90pt de haut** (`setHeight(90f)` + `SolidBorder(GRAY, 1f)`).

### Objectif du lot

Refondre la **présentation** des 4 rapports tabulaires — logo détouré, en-tête structuré,
tableau lisible, signature compacte — en s'inspirant des références de mise en page de
rapports/factures institutionnels, **sans changer le contenu** (mêmes données, colonnes,
rôles) et **sans nouvelle dépendance** (iText déjà en place).

### Périmètre

- ✅ **Les 4 rapports tabulaires** via `TabularExportService` / `PdfMetadata` / `PdfBranding`.
- ❌ **Hors périmètre** : `InvoicePdfService` (facture individuelle) et
  `RemittanceAdviceServiceImpl` (avis de règlement), qui construisent leur PDF
  indépendamment. Extension consignée dans `docs/future_ideas.md` (décision commanditaire).

---

## 2. Références (recherche web, 2026-07-12)

Convergences des sources sur la mise en page de rapports/factures pro :
- **Logo** haut-gauche ou centré, taille contenue (~150–200 px), de l'air autour.
- **Tableau** : traits fins, **éviter les bordures gris clair épaisses**, **lignes zébrées**
  pour lire les données, **répéter les en-têtes** si le tableau déborde d'une page.
- **Couleur de marque avec parcimonie** : filet d'en-tête, texte de marque, éventuellement
  bordure de la ligne total ; le reste noir-sur-blanc pour la lisibilité.
- **Signature** : même emplacement sur chaque page, **ligne** de signature (pas
  nécessairement un cadre), éviter la coupure entre deux pages. Espace négatif = lisibilité.

Sources : [Venngage — financial report format](https://venngage.com/blog/financial-report-format/),
[AccountingWare — formatting best practices](https://accountingware.com/activreporter/blog/formatting-best-practices-for-financial-statements),
[Invoicara — invoice layout guide](https://invoicara.com/blog/invoice-format-layout-guide),
[BrandedInvoice — invoice with logo](https://brandedinvoice.com/blog/invoice-template-with-logo/).

Ces références **valident les trois critiques** et cadrent la direction ci-dessous.

---

## 3. Direction visuelle (décisions brainstorming)

### a) Logo — corriger le bug
- Remplacer la source embarquée par le logo **détouré** existant :
  `assets/logos/oct-logo.png` (Afrique en dégradé bleu + « OWENDO CONTAINER TERMINAL » navy
  + « GABON » or, sur fond blanc/transparent). La version blanche
  `assets/logos/oct-logo-white.png` reste disponible pour un éventuel fond sombre.
- **Décision d'implémentation à trancher au plan** : soit remplacer le fichier
  `src/main/resources/branding/oct-logo.png` par le détouré (le plus simple, `PdfBranding`
  inchangé), soit repointer `PdfBranding.LOGO_RESOURCE`. Le résultat visuel est le même ;
  le plan choisira l'option la plus propre (probablement remplacer le fichier de ressource).
- Placé **haut-gauche**, largeur contenue (~150 pt), marge d'air en dessous.

### b) En-tête — logo à gauche, méta à droite
- **Ligne haute à deux colonnes** : à gauche le logo puis, dessous, le **titre du rapport**
  (gras, navy) ; à droite, alignées à droite, les métadonnées de `PdfMetadata.renderHeader`
  (générateur nom + rôle, date de génération) et la **période** si présente.
- **Filet fin sous l'en-tête** : trait navy avec **accent or** (rappel discipliné de
  l'identité, dans l'esprit du logo « GABON » souligné d'or), séparant l'en-tête du corps.
- `renderHeader` est **réorganisé** pour cette disposition à deux colonnes (aujourd'hui il
  empile période centrée + deux lignes méta à droite).

### c) Tableau — en-tête navy + zébrures, sans bordures verticales
- **Ligne d'en-tête** : fond **navy**, texte **blanc** gras.
- **Lignes de données zébrées** : alternance blanc / warm-neutral très pâle.
- **Séparateurs horizontaux fins** gris clair (~0.3 pt) ; **aucune bordure verticale** ;
  pas de bordure noire par défaut.
- **En-têtes répétés** si le tableau s'étend sur plusieurs pages (iText : en-tête de table).
- Montants / références en **chiffres tabulaires**, alignés à droite pour les montants.

### d) Signature — ligne, pas cadre
- Remplacer la boîte 90 pt par **deux lignes de signature fines** côte à côte ou empilées :
  « Signature ______ » et « Date ______ », **libellés en petit** dessous.
- **Pas de cadre bordé.**
- **Keep-together** : le bloc ne doit pas se couper entre deux pages.

---

## 4. Ce qui est HORS périmètre

- **Contenu** des rapports (données, colonnes, calculs, rôles, i18n des libellés) — inchangé.
- **Facture individuelle** (`InvoicePdfService`) et **avis de règlement**
  (`RemittanceAdviceServiceImpl`) — voir `docs/future_ideas.md`.
- **Nouvelle dépendance** (iText suffit).
- L'**enrichissement couleur de l'UI** (spec sœur, front).

---

## 5. Critères d'acceptation

- [ ] `PdfBranding` embarque le logo **détouré** (`assets/logos/oct-logo.png`) ; plus aucune
      capture à fond bleu/grue dans les PDF.
- [ ] En-tête à deux colonnes : logo + titre à gauche, méta (générateur/rôle, date, période)
      à droite ; **filet navy + accent or** sous l'en-tête.
- [ ] Tableau : en-tête **fond navy / texte blanc**, lignes **zébrées**, séparateurs
      horizontaux fins, **aucune bordure verticale** ni bordure noire par défaut ; en-têtes
      **répétés** en cas de débordement multi-pages.
- [ ] Signature : **lignes** Signature + Date, **sans cadre**, **keep-together** (pas de
      coupure de page).
- [ ] **Contenu inchangé** : mêmes données/colonnes/rôles ; les tests existants des 4
      rapports passent (`ReportMetadataTest`, `TabularExportServiceTest` adaptés au style,
      pas au contenu).
- [ ] **Vérif runtime** : **générer un PDF réel de chaque rapport** (audit, conformité,
      synthèse, builder) et l'**inspecter visuellement** (logo net, tableau lisible,
      signature compacte) — pas seulement « ça compile ».
- [ ] Backend `./mvnw test` : vert (aucun échec).
- [ ] Un commit par tâche, message `type(scope): description`.

---

## 6. Découpage en tâches (pour writing-plans)

1. **T1 — Logo détouré** : corriger la ressource embarquée par `PdfBranding` +
   dimension/placement ; vérifier sur un PDF généré.
2. **T2 — En-tête deux colonnes** : réorganiser `PdfMetadata.renderHeader` (logo+titre
   gauche, méta droite) + **filet navy/or**.
3. **T3 — Style de tableau** : en-tête navy, zébrures, séparateurs fins, pas de verticales,
   en-têtes répétés (dans `TabularExportService.toPdf`).
4. **T4 — Signature en lignes** : réécrire `PdfMetadata.renderSignatureBlock` (lignes,
   sans cadre, keep-together).
5. **T5 — Tests + vérif runtime** : adapter les tests de style ; générer et inspecter les 4
   PDF réels (light n/a — document imprimé) ; revue.

Chaque tâche : commit dédié + `./mvnw test` vert avant la suivante.

> Note d'architecture (non bloquante) : si T2–T4 factorisent des helpers réutilisables
> (branding, style de tableau, bloc signature) plutôt que de coder en dur dans
> `TabularExportService`, l'extension future à la facture / l'avis de règlement
> (`future_ideas.md`) se réduira à brancher ces helpers.
