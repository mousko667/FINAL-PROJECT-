# Idées gardées pour des évolutions futures

> Ce fichier consigne des options de conception délibérément écartées pour rester
> dans le périmètre (YAGNI), mais qui pourraient être implémentées plus tard si le
> besoin métier se confirme. Ne PAS implémenter sans une nouvelle décision explicite.

---

## B2 — Politique de rétention : passer du singleton à une liste de règles (Option B)

**Contexte :** B2 (2026-06-20) a implémenté la config de rétention en **singleton global**
(Option A) : une seule durée de rétention (années) + activation, lue par
`DocumentRetentionJob`. Voir `docs/superpowers/specs/2026-06-20-b2-retention-policy-design.md`.

**Option B écartée (à reconsidérer si le besoin apparaît) :** remplacer le singleton par
un **ensemble de règles** (CRUD, comme `EscalationRule` / `PaymentAlertRule`), avec une
durée de rétention **différente par type de document ou par département**.
Exemple : contrats = 10 ans, justificatifs = 7 ans, bons de commande = 5 ans.

**Ce que cela impliquerait :**
- Refonte de `DocumentRetentionJob` pour parcourir N règles au lieu d'un seuil unique.
- **Catégorisation des documents** : il faut un champ « type/catégorie » exploitable sur
  `InvoiceDocument` pour rattacher un document à une règle (n'existe pas aujourd'hui).
- Gestion des conflits / règle par défaut (document ne correspondant à aucune règle).
- UI : passer du formulaire d'édition à un tableau CRUD (réutiliser le pattern
  `EscalationRulesPage.tsx`).

**Pourquoi écarté pour l'instant :** le job actuel raisonne sur un seuil unique global ;
la conformité comptable (OHADA) impose généralement UNE durée d'archivage unique (10 ans).
Aucun besoin actuel de durées différenciées → décision prématurée (YAGNI).

**Chemin de migration :** la table singleton `retention_policy` pourrait devenir la « règle
par défaut » d'un futur modèle multi-règles, sans perte de données.

---

## M5 — Rapprochement (3-way matching) détaillé : ligne-à-ligne, historique, résolution

**Contexte :** le 3-way matching (PO/GRN/facture) est implémenté et fonctionne, mais au
**niveau montant/total** avec tolérance %/montant. Le résultat est intégré au détail facture
(`/invoices/:id`, panneau via `GET /invoices/{id}/matching`), pas dans une page dédiée.
`ThreeWayMatchingResult` est stocké en **append-only** ; l'UI n'affiche que le **dernier**
résultat. Items 🟠 restants dans `docs/TASKS.md §C` (M5) : UI #1, #4, #9, #10 + Features #2, #6, #7.

**Évolutions écartées pour l'instant (à reconsidérer si le besoin métier se confirme) :**
- **#4 Comparaison ligne-à-ligne** : afficher PO / GRN / facture côte à côte, ligne par ligne,
  au lieu de la comparaison globale sur le total. Suppose un appariement des lignes
  (référence article / désignation / quantité) entre les trois documents.
- **#9 Viewer historique matching** : page/onglet listant **toutes** les tentatives de
  rapprochement (la donnée append-only existe déjà en base ; manque un endpoint « liste »
  + un viewer front).
- **#10 Workflow résolution items non rapprochés** : résolution **ligne-par-ligne** dédiée,
  au lieu du seul override global qui débloque tout le statut MISMATCH.
- **#1 Page dédiée** : extraire le rapprochement du détail facture vers une page/onglet propre.

**Ce que cela impliquerait :**
- Back : DTO de comparaison ligne-à-ligne (appariement des lignes des 3 documents) ; endpoint
  d'historique listant les `ThreeWayMatchingResult` d'une facture ; modèle de résolution
  par ligne (statut + justification par ligne, distinct de l'override global).
- Front : page/onglet de rapprochement dédié, tableau de comparaison 3 colonnes, viewer
  d'historique, et UI de résolution ligne-par-ligne (réutiliser `MatchingBadge`).
- **Prérequis donnée** : un appariement fiable des lignes suppose des références d'article
  cohérentes entre PO, GRN et facture (à vérifier sur le modèle actuel).

**Pourquoi écarté pour l'instant :** le rapprochement au niveau montant couvre déjà la
détection d'écart et le blocage (MISMATCH bloque au-delà de SOUMIS) ; l'override avec
justification fournit déjà un chemin de résolution auditée. La comparaison ligne-à-ligne est
le plus gros chantier du lot C (back + front) pour un gain marginal sur le périmètre PFE → YAGNI.

**Chemin de migration :** le panneau matching de `InvoiceDetailPage` pourrait devenir un
onglet de la future page dédiée ; `ThreeWayMatchingResult` étant déjà append-only, le viewer
d'historique se branche sans changement de modèle.

---

## M2/M6 #3 — Widget « aging analysis » (balance âgée) sur le dashboard finance

**Contexte :** la balance âgée complète (table par tranches d'ancienneté) **existe déjà**
côté back (`AgingReportDTO`) et est exposée dans Rapports/Paiements. Le dashboard de
l'Assistant Comptable (finance) n'affiche aujourd'hui qu'un KPI « Factures en retard », pas
la table d'aging par tranches. Item 🟠 dans `docs/TASKS.md §C` (M2/M6 UI #3) ; voir aussi gap **G5**.

**Évolution écartée pour l'instant (à reconsidérer si le besoin se confirme) :** remonter un
**widget aging** (table/graphe par tranches : 0-30 / 31-60 / 61-90 / 90+ jours) directement
sur le dashboard finance, en plus de sa présence dans Rapports/Paiements.

**Ce que cela impliquerait :**
- Front uniquement (ou quasi) : la donnée `AgingReportDTO` existe déjà ; il s'agit de la
  consommer dans un composant widget du dashboard AA (`DashboardPanels.tsx` / `DashboardPage.tsx`).
- Pas de migration ni de nouveau endpoint si l'endpoint aging existant suffit.

**Pourquoi écarté pour l'instant :** l'information d'aging est **déjà accessible** (Rapports +
KPI « Factures en retard » sur le dashboard) ; en faire un widget supplémentaire est un gain
surtout **cosmétique/ergonomique**, sans nouvelle capacité métier → faible priorité.

**Chemin de migration :** réutiliser directement `AgingReportDTO` et son endpoint ; aucun
changement de modèle requis.

---

## M4 #4 — Routage par seuil : saut de niveau automatique (Option B)

**Contexte :** M4 #4 (routage d'approbation par montant) est implémenté en **Option A**
(2026-06-20) : une **garde de limite d'approbation** vérifie à chaque étape (`validateN1`,
`validateN2`, `bonAPayer`) que `approver.approvalLimit >= montant facture`, sinon la
validation est **bloquée** (escalade organisationnelle : un approbateur habilité, in fine le
DAF à limite illimitée, doit agir). Le routage reste piloté par le département (N1→N2→DAF) ;
la garde ne déplace pas la facture, elle refuse l'approbateur insuffisant.

**Option B écartée (à reconsidérer si le besoin se confirme) :** **saut de niveau
automatique** — si le montant dépasse la limite du niveau courant, le workflow route
**automatiquement** la facture vers le niveau supérieur (ex. N1 → directement N2 ou DAF),
sans qu'un approbateur insuffisant ait à agir d'abord.

**Ce que cela impliquerait :**
- Modifier le **routage des transitions** dans la machine à états (`InvoiceStateMachineService`
  / `InvoiceEvent`), pas seulement ajouter une garde : une même transition (`VALIDATE_N1`)
  mènerait à des états différents selon le montant.
- Gérer la collision avec la règle département `requiresN2` (ex. département à 1 niveau dont
  le montant exigerait un N2 inexistant pour ce département → règle par défaut à définir).
- Multiplier les cas de test (montant × config département × niveaux).

**Pourquoi écarté pour l'instant :** l'Option A applique déjà le contrôle dur (séparation des
pouvoirs) avec un risque de régression quasi nul ; le saut automatique touche le **cœur du
routage** (zone sensible) pour un gain d'ergonomie, hors périmètre PFE → YAGNI.

**Chemin de migration :** la garde de l'Option A (comparaison limite/montant) reste valable
et peut alimenter la décision de saut ; il faudrait l'étendre côté machine à états plutôt que
côté service de validation.

---

## R9 — Historique de rapprochement par facture (viewer) — choix de périmètre

**Contexte :** R9 (PROJECT_REPORT §12, **P2 optionnel**) propose un viewer d'**historique de
matching** : `GET /matching/{invoiceId}/history` listant **toutes** les lignes
`ThreeWayMatchingResult` d'une facture (et non le seul dernier résultat), plus un viewer front.
Depuis la rédaction de l'entrée M5 ci-dessus, **#1 (page dédiée `/matching`) et #4 (comparaison
ligne-à-ligne)** ont été livrés (M5 #1+#4, 2026-06-21) ; l'historique complet (#9) reste le
dernier reliquat de ce polish.

**Décision (2026-06-26) : écarté pour le PFE, documenté comme choix de périmètre.** Le
PROJECT_REPORT autorise explicitement cette option (« Sinon, documente que c'est un choix de
périmètre »).

**Pourquoi c'est défendable sans perte :**
- `ThreeWayMatchingResult` est **append-only** → toutes les tentatives passées sont **déjà
  conservées en base** ; il ne manque qu'un écran de consultation, aucune donnée n'est perdue.
- L'évolution dans le temps est déjà traçable via `audit_logs` et `invoice_status_history`.
- La page `/matching` (état courant + comparaison ligne-à-ligne) couvre le besoin opérationnel.
- → R9 est un **confort de consultation**, pas une capacité métier manquante (YAGNI).

**Ce que cela impliquerait (si repris) :** endpoint `GET /matching/{invoiceId}/history` (rôles
DAF / ASSISTANT_COMPTABLE, ADMIN/SUPPLIER exclus pour SoD) renvoyant la liste ordonnée des
`ThreeWayMatchingResult` ; viewer front (timeline des tentatives) ; tests + i18n FR/EN.
**Pas de migration** (données existantes). Voir l'item **#9** de la section M5 ci-dessus.

---

## T5 / M5 #10 — Résolution ligne-par-ligne des écarts de rapprochement

**Contexte (vérifié dans le code 2026-06-27) :** le rapprochement 3-voies, sa **page dédiée
`/matching`** et la **comparaison ligne-à-ligne** (M5 #1+#4, 2026-06-21) sont livrés ;
l'historique des tentatives (#9) a été écarté comme choix de périmètre (voir entrée **R9**
ci-dessus). Le **dernier reliquat** est la **résolution** : aujourd'hui le seul chemin de
déblocage d'un statut `MISMATCH` est l'**override global** —
`ThreeWayMatchingService.recordOverride(invoiceId, user, reason)` (DAF/ADMIN, raison ≥ 10
caractères, `ThreeWayMatchingResult` append-only) qui débloque **toute** la facture d'un coup.
Il n'existe **aucune** résolution au niveau d'une **ligne** d'écart individuelle.

**Évolution écartée pour l'instant (décision 2026-06-27, T5) :** un **workflow de résolution
ligne-par-ligne** — marquer/justifier chaque ligne en écart indépendamment (accepter/refuser
une ligne précise avec motif), au lieu du seul override global tout-ou-rien.

**Ce que cela impliquerait :**
- Back : un modèle de résolution **par ligne** (statut + justification + auteur par ligne en
  écart), distinct de l'override global ; endpoint de résolution ciblant une ligne ; règle de
  recalcul du statut facture (MISMATCH → débloqué seulement quand **toutes** les lignes en
  écart sont résolues). `ThreeWayMatchingResult` restant append-only.
- Front : sur la page `/matching`, action de résolution **par ligne** dans le tableau de
  comparaison (réutiliser `MatchingBadge`), avec saisie de motif et trace de l'auteur.
- Rôles : DAF / ADMIN (mêmes droits que l'override actuel) ; SoD inchangé.

**Pourquoi écarté pour l'instant :** l'override global **avec justification** fournit déjà un
chemin de résolution **audité** et suffisant pour le périmètre PFE ; la comparaison
ligne-à-ligne (déjà livrée) rend l'écart **visible** ligne par ligne, ce qui couvre le besoin
d'analyse. Une résolution **transactionnelle par ligne** ajoute un sous-workflow (statuts
partiels, recalcul, cas limites) pour un gain marginal → YAGNI.

**Chemin de migration :** l'override global actuel reste valable et devient le cas « résoudre
toutes les lignes d'un coup » ; un futur modèle par ligne s'ajoute sans rompre l'append-only de
`ThreeWayMatchingResult` ni l'API d'override existante.

---

## M11 #7 — Tendances temporelles volume/valeur : extensions écartées

**Contexte :** M11 #7 / feature #6 (2026-06-21) implémente une tendance temporelle
**volume + valeur** par **mois** sur les **12 derniers mois glissants** (`?months`
configurable), agrégée sur `issueDate`, affichée en `ComposedChart` (barres montant +
ligne volume) dans une nouvelle section de `/reports`. Voir
`docs/superpowers/specs/2026-06-21-m11-7-volume-value-trends-design.md`.

**Extensions écartées pour l'instant (à reconsidérer si le besoin se confirme) :**
- **Filtre par département / fournisseur** sur la tendance (le builder de rapports custom,
  M11 #9, couvre déjà l'analyse sur-mesure multi-critères).
- **Export dédié du graphe** (image/PDF) : l'export global des rapports existe déjà.
- **Granularité hebdomadaire / trimestrielle** : le pas mensuel + `?months` couvre le besoin
  de tendance ; d'autres granularités multiplieraient les cas sans gain métier net.
- **Plage de dates libre (from/to)** : décision = fenêtre glissante `?months` (2 sélecteurs
  de date = plus de surface UI et de cas limites pour un gain marginal en PFE).

**Ce que cela impliquerait :**
- Filtre dept/fournisseur : ajouter des `@RequestParam` au endpoint + passer les filtres à
  `findAllWithFilters` (qui les supporte déjà) + sélecteurs côté front.
- Export : générer une image serveur ou capturer le SVG recharts côté client.
- Granularité paramétrable : un `?granularity=WEEK|MONTH|QUARTER` et un regroupement
  temporel adapté (réutiliser le pattern `WeekFields` du cash-flow pour l'hebdo).

**Pourquoi écarté pour l'instant :** la tendance mensuelle glissante répond directement à
l'item « volume and value trends » ; les filtres et granularités supplémentaires relèvent du
report builder déjà présent → YAGNI.

**Chemin de migration :** le endpoint `GET /reports/volume-trend?months=N` peut accueillir
des paramètres optionnels (`departmentId`, `supplierId`, `granularity`) sans rupture, et
`findAllWithFilters` accepte déjà dept/fournisseur.
