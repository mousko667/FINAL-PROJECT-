# Enrichissement couleur de l'UI — dosage « Soutenu »

**Date :** 2026-07-12
**Branche :** `feat/ui-color-enrichment` (depuis `main`)
**Statut :** Spec validée en brainstorming, prête pour writing-plans
**Prolonge :** [2026-07-12-design-system-foundation-design.md](2026-07-12-design-system-foundation-design.md)
(Lots 1 & 2, livrés et mergés) — cette spec **réutilise** ses tokens et primitives,
elle ne la modifie pas.
**Skills mobilisés :** `frontend-design` (direction visuelle), `ui-ux-pro-max`
(accessibilité couleur), `design-system` (tokens 3 couches).

---

## 1. Contexte et problème

Après les Lots 1 & 2 du design system, l'Up reste **délibérément sobre** : surfaces
warm-neutral quasi-blanches en light / quasi-noires en dark, la couleur étant réservée
au statut (badges, alertes). Seule la **sidebar** porte l'identité (navy + or).

Constat du commanditaire (2026-07-12) : l'interface est **terne** — « tout noir ou tout
blanc en dehors de la barre latérale ». Clarification : « garder le thème » signifiait
garder **l'ADN** (navy + or + esprit institutionnel), **pas** figer la sobriété
noir/blanc. Le besoin : **enrichir la palette de l'UI générale**, avec des couleurs
**déjà présentes dans le système**, pour la rendre plus vivante **sans la rendre
flashy** (contexte : logiciel de facturation d'entreprise, terminal portuaire,
CEMAC/Gabon).

La spec foundation **ne prévoyait aucun** enrichissement couleur (elle verrouille au
contraire la sobriété : or jamais en aplat §2, sémantiques réservées au statut §5b,
« aucun écran redesigné » §6). L'enrichissement est donc une **décision nouvelle**, d'où
cette spec propre.

### Objectif du lot

Appliquer un dosage couleur **« Soutenu »** (choix commanditaire, colonne C du
comparateur `docs/mockups/palette-comparateur.html`) à **quatre zones** de l'UI
générale, **sans introduire aucune couleur nouvelle** — uniquement les teintes déjà
définies dans `index.css` (navy, info bleu-ardoise, or/sable, warm-neutrals,
sémantiques) — et **en passant par les primitives partagées** pour garantir la
cohérence sans dette inline.

---

## 2. Principe directeur

**Zéro couleur nouvelle.** On n'ajoute que des **tokens d'application** (surfaces
teintées) qui pointent, via `var()`, vers les couleurs existantes — de sorte que
light/dark suivent automatiquement la cascade `.dark` (même mécanisme que le Lot 1).

**On modifie les primitives, pas les pages.** L'enrichissement passe par les composants
partagés créés aux Lots 1 & 2 (`Button`, `Card`/`Panel`, `Table`, `Tabs`, en-tête de
page). Modifier la primitive → toutes les pages héritent → cohérence garantie, aucune
retouche page par page, aucune dette inline.

**Garde-fous non négociables :**
1. **Sémantique intouchable** : `destructive` reste rouge (`--crit`) ; les couleurs
   sémantiques `pos/warn/hot/crit` ne servent qu'au **sens** (statut/alerte), jamais
   comme décor. Un bouton de suppression ne devient jamais bleu « pour faire joli ».
2. **Or discipliné** : l'or (`--oct-gold` / `--gold-deep`) reste rare — CTA premium +
   filets d'accent. **Jamais en aplat de fond.**
3. **WCAG AA** : chaque combinaison texte-sur-fond-teinté est vérifiée (contraste ≥ 4.5
   pour le texte normal, ≥ 3 pour le grand texte/éléments UI) en light **ET** dark
   avant d'être figée.

---

## 3. Section 1 — Tokens d'application (à ajouter dans `index.css`)

Définis une fois en `:root` (light) et redéfinis sous `.dark`, chacun exprimé à partir
d'un token existant. Valeurs de départ (à confirmer par la validation AA de la Section
5, dans l'ordre du plan) :

| Token | Rôle | Base (light) | Base (dark) |
|---|---|---|---|
| `--header-grad-from` | Début dégradé en-tête | `hsl(var(--oct-navy))` | navy éclaircie (`--primary` dark) |
| `--header-grad-to` | Fin dégradé en-tête | `hsl(var(--oct-navy-light))` | idem raise |
| `--header-accent` | Filet or sous en-tête | `hsl(var(--oct-gold))` | `hsl(var(--gold-deep))` |
| `--nav-tint` | Fond bandeau d'onglets | `hsl(var(--info) / 0.12)` | `hsl(var(--info) / 0.14)` |
| `--page-tint` | Fond zone de contenu | `hsl(var(--info) / 0.07)` | `hsl(var(--info) / 0.06)` |
| `--kpi-info-bg` | Fond KPI informatif | `hsl(var(--info) / 0.09)` | `hsl(var(--info) / 0.10)` |

Les KPI **d'alerte** réutilisent les tokens `-bg` existants (`--pos-bg`, `--warn-bg`,
`--crit-bg`) — déjà validés AA au Lot B1. Aucun nouveau token pour eux.

> Les opacités sont un **point de départ** ; la Section 5 peut les ajuster pour tenir
> l'AA. La règle prime sur la valeur exacte.

---

## 4. Section 2 — Les 4 zones (dosage Soutenu)

### a) En-têtes & barres d'outils de page
Bandeau titre en **dégradé navy** (`--header-grad-from` → `--header-grad-to`), **filet
or 3px** en bas (`--header-accent`), titre en blanc (light) / encre claire (dark),
fil d'ariane et sous-titre en or atténué. Appliqué au composant d'en-tête de page
partagé.

### b) Cartes KPI / tuiles de synthèse
**Règle « couleur = sens d'alerte seulement »** (choix commanditaire) :
- Une tuile ne prend une couleur **sémantique** (`pos`/`warn`/`crit` via son `-bg` +
  barre latérale 4px de la teinte pleine) **que si son chiffre porte un sens d'état**
  (ex. « factures en retard » → `crit` si > 0 ; « conformité OK » → `pos`).
- Les tuiles **purement informatives** (ex. « total factures ») prennent le fond
  **bleu-ardoise** `--kpi-info-bg` + barre latérale `info`.
- **Jamais** de couleur décorative sans signification → évite l'effet bariolé.
- Le mapping chiffre→sémantique est **piloté par une prop** de la primitive KPI
  (`tone="info|pos|warn|crit"`), défini par la page appelante selon la donnée. La
  logique métier (seuils) reste côté page ; la primitive ne fait que rendre le ton.

### c) Boutons
Via la primitive `Button` (variants `cva` existants) :
- `primary` : **dégradé navy** (navy-light → navy).
- `secondary` : **fond bleu-ardoise net** (`info` teinté) + bordure info.
- `ghost` : texte info, hover fond info léger.
- `gold` : CTA **premium rare** — seul endroit où l'or vit en fond (`--gold-deep`).
- `destructive` : **reste rouge** (`--crit`) — inchangé, sémantique.

### d) Fonds de page & navigation secondaire
- Zone de contenu : fond teinté `--page-tint`.
- Bandeau d'onglets / filtres (primitive `Tabs`) : fond `--nav-tint` ; onglet actif
  détaché sur surface + **sous-ligne or** ; texte actif navy (light) / info (dark).

---

## 5. Section 3 — Mise en œuvre & garde-fous

- **Tokens** ajoutés dans `index.css`, `:root` + `.dark`, pointant vers l'existant via
  `var()` → light/dark automatiques.
- **Primitives modifiées, pas les pages** : `Button`, `Card`/`Panel`, `Table`, `Tabs`,
  en-tête de page. Les ~58 pages héritent sans retouche.
- **Validation AA obligatoire** avant de figer : mesurer le contraste de chaque
  combinaison texte-sur-fond-teinté, light **et** dark. Outil : le script de palette
  `dataviz`/`ui-ux-pro-max` ou une mesure de contraste équivalente. On ne valide pas à
  l'œil.
- **Vérif runtime obligatoire** après implémentation : captures Playwright des vraies
  pages (Dashboard, Rapports, une page tableau, une page formulaire) en light **ET**
  dark, sur le compte adéquat (rappel SoD : `/reports` = `daf`, pas `admin`).

### Note technique — toggle de thème
Un défaut **pré-existant** de `hooks/useTheme.ts` (useState disjoints App/Header)
empêche certains sous-arbres de re-rendre au toggle runtime sans reload (documenté au
Lot 2, follow-up `docs/TASKS.md`). **Hors périmètre de cette spec** : la vérif dark se
fait via `localStorage['oct-theme']='dark'` + reload, comportement attendu.

---

## 6. Ce qui est HORS périmètre

- Le **PDF exportable** → spec sœur `2026-07-12-pdf-report-redesign-design.md`.
- La **sidebar** (déjà navy/or — elle EST la référence, on ne la touche pas).
- Tout **changement de police**.
- La correction du bug `useTheme` (follow-up séparé, lot thème dédié).
- Toute couleur **nouvelle** hors des tokens existants.

---

## 7. Critères d'acceptation

- [ ] Tokens d'application ajoutés dans `index.css` (`:root` + `.dark`), exprimés via
      `var()` sur des tokens existants ; aucune couleur nouvelle.
- [ ] Les 4 zones traitées en dosage Soutenu **via les primitives partagées**
      (`Button`, `Card`/`Panel`, `Table`, `Tabs`, en-tête de page) — aucune retouche
      couleur inline dans les pages.
- [ ] Règle KPI « couleur = sens d'alerte » implémentée via une prop `tone` de la
      primitive KPI ; tuiles informatives en bleu-ardoise.
- [ ] `destructive` reste rouge ; or resté rare (CTA + filets), jamais en aplat de fond.
- [ ] **Contraste WCAG AA** vérifié sur chaque combinaison texte-sur-fond-teinté, light
      ET dark (mesure, pas à l'œil).
- [ ] **Vérif runtime** : captures Playwright Dashboard + Rapports (compte `daf`) + une
      page tableau + une page formulaire, en light ET dark — aucune régression de
      lisibilité.
- [ ] `vitest` : 100 % vert (aucun échec « pré-existant » toléré).
- [ ] `tsc` : 0 erreur.
- [ ] Backend `./mvnw test` : inchangé (front-only).
- [ ] Un commit par tâche, message `type(scope): description`.

---

## 8. Découpage en tâches (pour writing-plans)

1. **T1 — Tokens d'application** (`index.css`, `:root` + `.dark`) **+ validation AA**
   des combinaisons prévues (ajuster les opacités si besoin).
2. **T2 — En-têtes de page** (primitive d'en-tête : dégradé navy + filet or).
3. **T3 — Cartes KPI** (prop `tone`, règle « sens d'alerte », mapping côté pages
   Dashboard/Rapports).
4. **T4 — Boutons** (variants `Button` : primary dégradé, secondary teinté, gold CTA).
5. **T5 — Fonds & navigation secondaire** (`--page-tint`, `Tabs` teinté + sous-ligne
   or).
6. **T6 — Vérif runtime** (captures Playwright light/dark, 4 écrans témoins) + revue.

Chaque tâche : commit dédié + gate vert (vitest + tsc) avant la suivante.
