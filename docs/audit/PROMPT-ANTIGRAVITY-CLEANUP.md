# PROMPT POUR ANTIGRAVITY — Nettoyage final des anomalies 🟠/🟡 (post-audit QA OCT invoice-system)

> Colle TOUT ce qui suit dans Antigravity. Ce prompt est autonome et complet.
> Réponds et travaille en **FRANÇAIS**. Ne fais QUE ce qui est demandé ici.

---

## 0. CONTEXTE & RÔLE

Tu interviens sur le projet **`invoice-system`** (chemin :
`c:\Users\Dany\Documents\FINAL PROJECT\invoice-system`), un système de gestion
des factures fournisseurs pour Owendo Container Terminal (OCT). C'est un projet
de fin d'études de qualité entreprise.

**Stack :** Spring Boot 3.4 · Java 21 · PostgreSQL 18 (host-natif, **port 5433**) ·
React 19 + TypeScript + Vite · MinIO · MailHog · Flyway · Docker.
**Langues :** FR (primaire) + EN (secondaire) — tout texte visible utilisateur est bilingue.

Un **audit QA exhaustif** vient d'être réalisé (rapport :
`docs/FINAL_QA_AUDIT_REPORT.md`). Il a **réfuté un faux « 100% PASS »** en trouvant
4 bugs 🔴 bloquants (déjà corrigés) puis a listé des défauts résiduels 🟠/🟡.
**Ta mission : nettoyer TOUS les défauts résiduels listés en §3 ci-dessous**, de
façon à ce que le système atteigne réellement 100 % (0 défaut majeur, 0 mineur).

Le développeur reviendra ensuite faire les **vérifications finales et les commits**
lui-même. **TOI, tu NE COMMITES PAS, tu NE PUSHES PAS** (sauf si on te le demande
explicitement). Tu corriges dans le working tree, tu testes, tu reportes.

---

## 1. LIRE D'ABORD CES FICHIERS (référence obligatoire, dans cet ordre)

1. `CLAUDE.md` (racine) — directive primaire, règles à ne jamais violer
2. `docs/FINAL_QA_AUDIT_REPORT.md` — le rapport d'audit complet (origine des ANO-xxx)
3. `docs/KNOWN_ISSUES_REGISTRY.md` — registre des bugs ; tu DOIS y ajouter chaque
   correctif (format PROB-NNN, voir §5)
4. `docs/TASKS.md` — roadmap vivante (§A Open Gaps, §B Out of Scope, §C statut par module)
5. `docs/CONVENTIONS.md` — style de code, anti-patterns connus
6. `docs/API.md` — contrat des endpoints
7. `docs/WORKFLOW.md` — règles métier BAP (Bon à Payer)

> ⚠ **Ne lis pas les fichiers de migration Flyway déjà appliqués pour les modifier.**
> V40 est la dernière. **NE JAMAIS modifier une migration déjà appliquée**
> (checksum verrouillé dans `flyway_schema_history` → PROB-009).

---

## 2. RÈGLES CRITIQUES DU PROJET (à respecter absolument)

- **`messages_fr.properties` est encodé en ISO-8859-1 (Latin-1), PAS UTF-8.**
  Si tu y ajoutes des lignes en UTF-8 brut, tu **corromps tous les accents** du fichier.
  → Utilise **`iconv`** pour convertir tes ajouts (`iconv` est dispo dans `/usr/bin/iconv`).
  → **N'utilise JAMAIS** d'em-dash (—), de guillemets courbes (« » " " ') ou d'apostrophe
    typographique ('). Utilise apostrophe droite `'`, tiret simple `-`, guillemets droits `"`.
  → `messages_en.properties` est en UTF-8 standard.
  Procédure recommandée pour ajouter des clés FR :
  ```bash
  cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
  # 1. écrire les nouvelles lignes dans un fichier temp UTF-8 (accents OK, AUCUN caractère typographique)
  cat > /tmp/fr_add.txt <<'EOF'
  mfa.already_enabled=La double authentification est deja activee pour votre compte
  EOF
  # 2. convertir en Latin-1 et APPENDER au fichier FR
  iconv -f UTF-8 -t ISO-8859-1 /tmp/fr_add.txt >> src/main/resources/i18n/messages_fr.properties
  ```
  (Astuce : tu peux mettre les vrais accents dans le temp UTF-8 ; iconv les convertit
  correctement en Latin-1. Le seul interdit, ce sont les caractères typographiques
  hors table Latin-1 : —, « », " ", ', …)

- **Architecture (CLAUDE.md §3) :** jamais de logique métier dans un contrôleur ;
  toujours DTO (jamais d'entité exposée) ; toujours `@PreAuthorize` ; réponses
  enveloppées dans `ApiResponse<T>` ; textes via `MessageSource` (jamais en dur) ;
  `@Slf4j` (jamais `System.out.println`).

- **i18n frontend :** clés dans `frontend/src/i18n/fr.json` et `en.json` (UTF-8, JSON).
  Tout libellé doit exister dans les DEUX fichiers et avoir la même structure.

- **NE PAS toucher au travail user en cours** présent dans le working tree :
  tout ce qui touche `ArchiveFolder*`, `Backup*`, les migrations `V36`→`V40`,
  `ThreeWayMatchingLineResolution*`. Ce sont des fonctionnalités du développeur,
  pas des anomalies. (Tu PEUX éditer `frontend/.../AdminBackupsPage` côté i18n
  seulement — voir ANO-006 — mais sans changer sa logique.)

---

## 3. ANOMALIES À CORRIGER (liste exhaustive avec état réel vérifié)

> 🟢 = déjà corrigé, **NE PAS Y TOUCHER**. 🟠 = majeur à corriger. 🟡 = mineur à corriger.

### 🟢 DÉJÀ CORRIGÉS — NE RIEN FAIRE (vérifié le 2026-06-29)
- **ANO-001 / V38** : migration corrigée (`purchase_order_items`). ✅
- **ANO-002** : payload `departmentId` (création facture). ✅
- **ANO-003** : `LazyInitializationException` (resolveDepartment + `@EntityGraph`). ✅
- **ANO-004** : test vitest `PaymentsPage.test.tsx:65` corrigé (badge ciblé par tag SPAN). ✅
- **ANO-007** : `InvoiceService.java:270` lève déjà la bonne clé
  `error.invoice.document_required` (présente en FR+EN). Plus aucune trace de
  `error.invoice.no_document`. ✅ **NE PAS modifier.**
- **ANO-008** : colonne « Département ». Le mapper backend
  (`InvoiceMapper.java:11-14`) peuple bien `departmentId/Code/NameFr/NameEn`, et le
  front (`InvoiceListPage.tsx:234`, `InvoiceDetailPage.tsx:202`) lit déjà
  `departmentNameFr/En ?? departmentCode ?? '—'`. ✅ **NE PAS modifier.**
  (Si tu veux, vérifie juste au runtime que la colonne s'affiche bien — voir §4 —
  mais NE change PAS le code, il est correct.)

---

### 🟠 ANO-005 — 15 clés i18n FR manquantes (cluster MFA / lockout) — BACKEND
- **Fichier :** `src/main/resources/i18n/messages_fr.properties` (**ISO-8859-1** — voir §2 !)
- **Problème :** 15 clés existent en EN mais PAS en FR. FR étant la langue primaire,
  l'UI MFA/lockout affiche la clé brute ou l'anglais.
- **Action :** ajouter ces 15 clés en FR. Valeurs EN de référence + traduction FR
  proposée (à appender via `iconv`, SANS caractère typographique) :

  | Clé | FR à ajouter |
  |---|---|
  | `mfa.already_enabled` | La double authentification est deja activee pour votre compte |
  | `mfa.confirm.success` | Configuration de la double authentification confirmee avec succes |
  | `mfa.enabled.success` | La double authentification a ete activee avec succes |
  | `mfa.qr.backup_codes` | Conservez vos codes de secours dans un endroit sur |
  | `mfa.qr.generate` | Scannez le code QR avec votre application d'authentification |
  | `mfa.qr.manual_entry` | Ou saisissez cette cle manuellement : {0} |
  | `mfa.setup.required` | La configuration de la double authentification est requise. Veuillez la finaliser pour continuer. |
  | `mfa.setup.start` | Demarrer la configuration de la double authentification |
  | `mfa.verification.enter_code` | Saisissez le code a 6 chiffres de votre application d'authentification |
  | `mfa.verification.invalid` | Code de verification invalide ou expire. Veuillez reessayer. |
  | `error.otp.expired` | Le mot de passe a usage unique a expire |
  | `error.otp.invalid` | Mot de passe a usage unique invalide |
  | `error.account.locked` | Compte verrouille suite a un trop grand nombre de tentatives echouees. Veuillez reessayer plus tard ou contacter votre administrateur. |
  | `error.login.attempts_exceeded` | Trop de tentatives de connexion echouees. Votre compte a ete verrouille pendant 15 minutes. |
  | `action.unlock.success` | Compte utilisateur deverrouille avec succes |

- **Vérif :** après ajout, `grep -c "^mfa.already_enabled=" messages_fr.properties` doit
  renvoyer `1`. Et `./mvnw -q -o compile` doit rester EXIT 0. Surtout, **rouvrir le
  fichier et vérifier qu'aucun accent existant n'a été corrompu** (chercher des `Ã©`
  ou caractères bizarres = signe de corruption UTF-8).

---

### 🟠 ANO-006 — 10 clés i18n FR manquantes (bloc `backups`) — FRONTEND
- **Fichier :** `frontend/src/i18n/fr.json` (UTF-8, JSON)
- **Problème :** le bloc `"backups"` existe en EN (`en.json`) mais est ABSENT de `fr.json`.
  → `pages/admin/AdminBackupsPage.tsx` affiche les clés brutes en FR.
- **Action :** ajouter dans `fr.json` un bloc `"backups"` (au bon emplacement
  alphabétique/structurel, comme les autres blocs), traduction du bloc EN :
  ```json
  "backups": {
    "navTitle": "Sauvegardes",
    "title": "Sauvegarde et restauration",
    "description": "Gérer les sauvegardes de la base de données",
    "createBtn": "Créer une sauvegarde",
    "lastStatus": "Dernier statut",
    "empty": "Aucune sauvegarde disponible",
    "filename": "Fichier de sauvegarde",
    "restoreBtn": "Restaurer",
    "restoreConfirmTitle": "Restaurer le système",
    "restoreConfirmDesc": "Voulez-vous vraiment simuler une restauration depuis {{file}} ? En production, cela écraserait les données actuelles."
  }
  ```
  (Le `fr.json` est UTF-8 : les vrais accents é à è sont OK ici, PAS de souci d'encodage.)
- **Vérif :** `npx tsc --noEmit` reste EXIT 0 ; ouvrir AdminBackupsPage en FR au runtime
  (optionnel) → libellés traduits.

---

### 🟡 ANO-009 — Format de date non localisé (US) sur la page Paiements — FRONTEND
- **Fichier :** `frontend/src/pages/PaymentsPage.tsx`
- **Problème :** `new Date(p.paymentDate).toLocaleDateString()` (ligne ~441) sans locale
  → affiche `6/29/2026` (format US) sur l'UI française au lieu de `29/06/2026`.
  Idem `toLocaleString()` pour les montants (lignes ~85, ~356, ~438) qui devraient
  suivre la locale active.
- **Action :** localiser le formatage selon `i18n.language`. Pattern recommandé :
  - récupérer la locale active via `useTranslation()` → `i18n.language`
    (`'fr'` ou `'en'`) ;
  - dates : `new Date(p.paymentDate).toLocaleDateString(i18n.language === 'en' ? 'en-US' : 'fr-FR')` ;
  - montants : `Number(x).toLocaleString(i18n.language === 'en' ? 'en-US' : 'fr-FR')`.
  - **Mieux** : vérifie s'il existe déjà un util de formatage dans
    `frontend/src/utils/` ou `frontend/src/lib/` (cherche `formatDate`, `formatCurrency`,
    `Intl.DateTimeFormat`). **Si un helper existe, RÉUTILISE-le** plutôt que de
    dupliquer la logique inline (cohérence projet). Sinon, applique le pattern ci-dessus.
- **Attention test :** `PaymentsPage.test.tsx` vient d'être corrigé (ANO-004). Après ta
  modif, relance `npx vitest run src/test/pages/PaymentsPage.test.tsx` → doit rester vert.

---

### 🟡 ANO-010 — En-têtes d'export CSV/Excel en anglais sur l'UI française — BACKEND
- **Problème :** plusieurs exports ont des en-têtes **codés en dur en anglais**, même
  déclenchés depuis l'UI française. Or il existe déjà un pattern i18n correct
  (`ReportServiceImpl.java:242-249` utilise les clés `report.excel.header.*`, qui
  EXISTENT déjà en FR ET EN — 8 clés chacune).
- **Emplacements à corriger (en-têtes en dur) :**
  1. `src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceController.java:107`
     → `List.of("Reference", "Supplier", "Amount", "Currency", "Status", "Issue date", "Due date", "Department")`
  2. `src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceController.java:183`
     → `List.of("Field", "Value")` (rapport de matching)
  3. `src/main/java/com/oct/invoicesystem/domain/report/service/ReportBuilderService.java:98`
     → `List.of("Reference", "Supplier", "Amount", "Currency", "Status", "Issue date", "Due date", "Department")`
- **Action :** remplacer ces listes en dur par des libellés résolus via `MessageSource`
  + la locale de la requête (`LocaleContextHolder.getLocale()` ou l'`Accept-Language`),
  EXACTEMENT comme le fait `ReportServiceImpl.java:242-249`.
  - Réutilise les clés `report.excel.header.reference/supplier/amount/currency/status/issue_date/due_date/department` (déjà présentes FR+EN) pour le cas #1 et #3.
  - Pour le cas #2 (`Field`/`Value`), crée 2 nouvelles clés i18n
    (`report.excel.header.field`, `report.excel.header.value`) en FR (ISO-8859-1 via iconv !)
    ET EN.
  - **Important** : ne casse pas la signature publique des endpoints ; injecte
    `MessageSource` dans le contrôleur via le service approprié si nécessaire, en
    respectant l'archi (idéalement, déplace la construction des en-têtes dans la couche
    service, pas dans le contrôleur — vois comment `ReportServiceImpl` est structuré et
    aligne-toi dessus).
- **Vérif :** `./mvnw -q -o compile` EXIT 0 ; idéalement déclencher un export depuis
  l'UI FR et confirmer les en-têtes traduits (voir §4).

---

### 🟡 ANO-011 — Doublons d'endpoints POST + PATCH activate/suspend — BACKEND
- **Fichier :** `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java`
- **État réel :** il y a 2 paires de doublons :
  - `@PostMapping("/{id}/activate")` (l.114) **+** `@PatchMapping("/{id}/activate")` (l.122, délègue à la version POST)
  - `@PostMapping("/{id}/suspend")` (l.128) **+** `@PatchMapping("/{id}/suspend")` (l.136, délègue à la version POST)
- **Problème :** redondance de compatibilité, non nuisible mais à rationaliser.
- **⚠ AVANT de supprimer quoi que ce soit :** vérifie quel verbe le **frontend** utilise
  réellement. Cherche dans `frontend/src/` les appels à `/activate` et `/suspend`
  (`grep -rn "activate\|suspend" frontend/src/`). **Garde le verbe utilisé par le
  front, supprime l'autre.** Si le front utilise PATCH, supprime les `@PostMapping` ;
  s'il utilise POST, supprime les `@PatchMapping`. Si AUCUN n'est utilisé clairement,
  garde **PATCH** (sémantiquement correct pour un changement d'état partiel) et
  documente le choix.
- **Aligne** aussi `docs/API.md` si le verbe documenté change.
- **Vérif :** `./mvnw -q -o compile` EXIT 0 ; relancer les tests d'intégration du
  SupplierController s'ils existent (`./mvnw -q -o test -Dtest=*Supplier*`).

---

### 🟡 ANO-012 — Logs périmés à la racine — NETTOYAGE
- **Problème :** fichiers de logs obsolètes à la racine du projet
  (`build-errors.txt` ~2026-04-07, `compile-errors.txt` ~2026-04-06, `perf.log`,
  `test-logs.txt` ~2026-04-12). Ils reflètent des états résolus depuis >2 mois et
  peuvent induire en erreur un futur audit.
- **Action :**
  1. Vérifier qu'ils sont bien obsolètes (`ls -la *.txt *.log` à la racine, regarder dates).
  2. Les **supprimer** (`rm`). NE supprime QUE ces fichiers de logs périmés, rien d'autre.
  3. Vérifier/ajouter au `.gitignore` les patterns pour éviter qu'ils reviennent
     (`*.log`, `build-errors.txt`, `compile-errors.txt`, `test-logs.txt`, `perf.log`).
     Le `.gitignore` est déjà modifié dans le working tree — ajoute proprement sans
     dupliquer les entrées existantes.
- **NE PAS** supprimer de logs récents/pertinents ni quoi que ce soit hors de ces fichiers.

---

### ⚪ ANO-013 — NE PAS traiter (hors-périmètre / décision développeur)
- SLA dashboard dédié, planification cron des rapports, distribution email auto
  (Modules 4/6/11). Ce sont des **partiels architecturaux assumés**, PAS des bugs.
  **Ne les implémente PAS** (ce serait inventer du scope). Note-les simplement comme
  open gaps dans `docs/TASKS.md §A` s'ils n'y sont pas déjà.

---

## 4. COMMENT LANCER L'ENVIRONNEMENT (pour vérifs runtime — optionnel mais recommandé)

> Le backend tourne en **jar host** (PAS docker). Repackager après tout changement backend.

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"

# --- BACKEND ---
# 1. repackager (offline, ~5 min)
./mvnw -q -o -DskipTests package
# 2. tuer l'ancien process sur 8080
PID=$(powershell -Command "(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue).OwningProcess" | tr -d '\r' | head -1)
[ -n "$PID" ] && powershell -Command "Stop-Process -Id $PID -Force"
# 3. charger les secrets depuis .env et lancer
set -a; source <(grep -E "^(DB_PASSWORD|DB_USER|DB_NAME|MINIO_ACCESS_KEY|MINIO_SECRET_KEY|MINIO_BUCKET|ENCRYPTION_KEY|JWT_PRIVATE_KEY|JWT_PUBLIC_KEY)=" .env); set +a
export DB_HOST=localhost DB_PORT=5433 MINIO_ENDPOINT=http://localhost:9000 \
       MAIL_HOST=localhost MAIL_PORT=1025 SPRING_PROFILES_ACTIVE=dev APP_PORT=8080
java -jar target/invoice-system-1.0.0-SNAPSHOT.jar   # lancer en arrière-plan
# santé : curl http://localhost:8080/actuator/health  -> {"status":"UP"}

# --- FRONTEND ---
cd frontend && npm run dev        # port 3000, HMR
```

**Comptes test (mot de passe `Test1234!`) :**
`aa` (ASSISTANT_COMPTABLE), `daf` (DAF = N1 Finance), `rsi` (N1 INFO),
`dsi` (N2 INFO), `supplier`. (`admin` a un autre mot de passe inconnu — hash V5.)

**PostgreSQL :** `psql.exe` dispo (`/c/Program Files/PostgreSQL/17/bin/psql.exe`),
DB_PASSWORD dans `.env`, host `localhost` port `5433`.

**Dept IDs utiles :** FIN=`cfca168e-f986-41d4-8d4e-9c00aed16d57`,
INFO=`05cc38e9-b284-4fc0-bd7a-daefa735b0f2`.

---

## 5. APRÈS CHAQUE CORRECTIF — DOCUMENTATION OBLIGATOIRE (CLAUDE.md §12)

Pour CHAQUE anomalie corrigée, ajouter une entrée dans
`docs/KNOWN_ISSUES_REGISTRY.md` (numéros déjà réservés par l'audit) :
- **PROB-069** = ANO-001/V38 (déjà à logger si pas fait — la cause racine est dans le rapport)
- **PROB-070** = ANO-002 (departmentId)
- **PROB-071** = ANO-003 (lazy mapping)
- **PROB-072** = ANO-007 (clé no_document → document_required)
- **PROB-073** = ANO-008 (colonne Département)
- **Continue la numérotation** pour tes corrections : PROB-074 (ANO-005), PROB-075
  (ANO-006), PROB-076 (ANO-009), PROB-077 (ANO-010), PROB-078 (ANO-011), PROB-079 (ANO-012).

Format de chaque entrée :
```
### PROB-0NN — {titre court} (ANO-0XX)
- Cause racine : {POURQUOI, pas juste quoi}
- Solution : {fichiers + changements exacts}
- Règle préventive : {comment ne jamais reproduire}
- Date : 2026-06-29
```

Mets aussi à jour `docs/TASKS.md` (statut module / open gaps) si pertinent.

---

## 6. VÉRIFICATIONS À EXÉCUTER AVANT DE RENDRE LA MAIN (preuves obligatoires)

Lance ces commandes et **colle les sorties réelles** dans ton rapport final
(pas de « ça devrait passer » — montre la preuve) :

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"

# Backend compile + tests
./mvnw -q -o compile                 # attendu : EXIT 0
./mvnw -q -o test                    # attendu : 0 failure, 0 error (BUILD SUCCESS)

# Frontend type-check + tests + build
cd frontend
npx tsc --noEmit                     # attendu : EXIT 0
npx vitest run                       # attendu : TOUS verts (le total était 80, dont 1 rouge déjà corrigé -> 100% vert)
npm run build                        # attendu : EXIT 0
```

> ⚠ **RÈGLE PROJET « no failures on completion » :** une tâche n'est « terminée »
> QUE si 0 test échoue / 0 erreur. Pas d'excuse « pré-existant ». Si un test casse à
> cause de tes changements, corrige-le proprement (ne le skip pas, ne le supprime pas).

**Vérif anti-corruption i18n FR (critique après ANO-005/010) :**
```bash
# Le fichier doit rester du Latin-1 valide, aucun accent corrompu :
grep -nE "Ã©|Ã¨|Ã |Â" src/main/resources/i18n/messages_fr.properties   # attendu : AUCUNE sortie
file src/main/resources/i18n/messages_fr.properties                     # attendu : ISO-8859 / data, PAS "UTF-8"
```

---

## 7. CE QUE TU NE DOIS PAS FAIRE

- ❌ Ne committe pas, ne push pas (le développeur s'en charge).
- ❌ Ne touche pas aux anomalies 🟢 déjà corrigées (ANO-001/002/003/004/007/008).
- ❌ Ne modifie aucune migration Flyway déjà appliquée (≤ V40).
- ❌ Ne touche pas au travail user en cours (ArchiveFolder*, Backup* logique,
  ThreeWayMatchingLineResolution*, V36→V40).
- ❌ N'implémente pas ANO-013 (hors-périmètre).
- ❌ N'ajoute pas de fichiers non demandés (pas de NOTES.md, SUMMARY.md, etc. — CLAUDE.md §4).
- ❌ N'ajoute JAMAIS d'em-dash / guillemets courbes / apostrophes typographiques dans
  `messages_fr.properties`.

---

## 8. RAPPORT FINAL ATTENDU (en français)

À la fin, rends un rapport contenant :
1. Pour chaque ANO traité (005, 006, 009, 010, 011, 012) : état AVANT → APRÈS +
   fichiers modifiés.
2. Les sorties RÉELLES des 5 commandes de vérif du §6 (+ vérif anti-corruption i18n).
3. La liste des entrées ajoutées à `KNOWN_ISSUES_REGISTRY.md` (PROB-074 → PROB-079).
4. Toute anomalie que tu n'as PAS pu corriger, avec la raison (blocker report clair).
5. Confirmation que tu n'as RIEN commité ni poussé.

---

### Résumé en une ligne
Corrige les anomalies 🟠/🟡 résiduelles (ANO-005, 006, 009, 010, 011, 012) du système
OCT invoice-system, en respectant l'encodage Latin-1 du FR, l'archi en couches et la
règle 0-échec, documente chaque fix dans KNOWN_ISSUES_REGISTRY, NE COMMITE PAS, et
rends un rapport avec preuves de vérification.
