# PROMPT — Vérification finale + commits (post-nettoyage audit QA OCT invoice-system)

> Nouvelle session. Travaille et réponds en **FRANÇAIS**.
> Invoque d'abord la skill `superpowers:verification-before-completion` (preuve avant
> toute affirmation de succès), puis `superpowers:systematic-debugging` si un test casse.

---

## 0. CONTEXTE

Projet : **`invoice-system`** (`c:\Users\Dany\Documents\FINAL PROJECT\invoice-system`),
système de gestion des factures fournisseurs OCT — projet de fin d'études qualité entreprise.
Stack : Spring Boot 3.4 / Java 21 / PostgreSQL 18 (host-natif **port 5433**) / React 19 + TS +
Vite / MinIO / MailHog / Flyway. Langues : FR (primaire) + EN (secondaire).

Un **audit QA exhaustif** a réfuté un faux « 100% PASS » (rapport :
`docs/FINAL_QA_AUDIT_REPORT.md`). Les **4 bugs 🔴 bloquants** ont été corrigés (V38,
payload departmentId, LazyInitializationException ×2). Ensuite, **toutes les anomalies
résiduelles 🟠/🟡 ont été nettoyées** (par moi puis par Antigravity, sessions précédentes).

**État DÉJÀ vérifié par grep dans le working tree (ne pas refaire, juste savoir) :**
- ANO-004 ✅ test vitest PaymentsPage corrigé · ANO-005 ✅ 15 clés MFA/lockout en FR ·
  ANO-006 ✅ bloc `backups` en fr.json · ANO-007/008 ✅ (étaient déjà corrigés) ·
  ANO-009 ✅ dates/montants localisés · ANO-010 ✅ en-têtes export i18n ·
  ANO-011 ✅ doublons endpoints rationalisés (1 verbe par action) · ANO-012 ✅ logs périmés supprimés.
- `messages_fr.properties` reste ISO-8859-1, **aucun accent corrompu** (grep `Ã©|Ã¨|—` = vide).

**TA MISSION dans cette session :**
1. Faire les **vérifications finales** (builds + tests + runtime) avec PREUVES réelles.
2. Si tout est vert, faire les **commits séparés par thème** (voir §3) — PUIS demander si on pousse.

---

## 1. RÈGLES CRITIQUES (à respecter)

- **Réponds en FRANÇAIS.**
- **`messages_fr.properties` = ISO-8859-1.** Ne JAMAIS y ré-injecter d'UTF-8 brut / em-dash /
  guillemets courbes. (On ne devrait plus y toucher ici, juste vérifier.)
- **Règle « no failures on completion » :** rien n'est « terminé » tant qu'un seul test échoue
  ou erreur. Pas d'excuse « pré-existant ». Si un test casse → skill systematic-debugging,
  trouve la cause racine, corrige proprement (ne skip pas, ne supprime pas le test).
- **Migrations Flyway :** V40 = dernière. NE JAMAIS modifier une migration déjà appliquée (PROB-009).
- **Backend = jar host (PAS docker).** Repackager après tout changement backend.
- **Le working tree mélange 2 sources** : (a) le travail user en cours (ArchiveFolder*, Backup*,
  ThreeWayMatchingLineResolution*, migrations V36→V40, untracked) et (b) les correctifs d'audit
  (fichiers modifiés). **Stratégie de commit validée par le user = commits SÉPARÉS PAR THÈME**
  (one-commit-one-topic). NE PAS tout mélanger dans un seul commit.
- **Ne committe/ne pousse QUE quand le user le confirme.** Prépare, montre, demande.

---

## 2. VÉRIFICATIONS FINALES — exécuter et COLLER LES SORTIES RÉELLES

> Preuve obligatoire : pas de « ça devrait passer ». Montre la sortie de chaque commande.

### 2.1 Builds + tests statiques
```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"

# Backend
./mvnw -q -o compile            # attendu : EXIT 0
./mvnw -q -o test               # attendu : BUILD SUCCESS, 0 failure / 0 error

# Frontend
cd frontend
npx tsc --noEmit                # attendu : EXIT 0
npx vitest run                  # attendu : 100% vert (80 tests, le rouge ANO-004 est corrigé)
npm run build                   # attendu : EXIT 0
cd ..
```

### 2.2 Anti-corruption i18n FR (critique)
```bash
grep -nE "Ã©|Ã¨|Ã |Â|—" src/main/resources/i18n/messages_fr.properties   # attendu : AUCUNE sortie
file src/main/resources/i18n/messages_fr.properties                      # attendu : ISO-8859 / data (PAS UTF-8)
# Parité des 15 clés MFA/lockout FR (chacune doit valoir 1) :
for k in mfa.already_enabled mfa.confirm.success mfa.enabled.success mfa.qr.backup_codes mfa.qr.generate mfa.qr.manual_entry mfa.setup.required mfa.setup.start mfa.verification.enter_code mfa.verification.invalid error.otp.expired error.otp.invalid error.account.locked error.login.attempts_exceeded action.unlock.success; do echo -n "$k:"; grep -c "^$k=" src/main/resources/i18n/messages_fr.properties; done
```

### 2.3 Vérification RUNTIME (la plus importante — c'est ce qui a démasqué le faux « 100% PASS »)
> ⚠ Un DOM correct peut masquer un GET cassé. Vérifie la trace réseau, pas juste l'écran
> (cf. la leçon de l'audit : tout semblait vert sur base vide).

**Lancer l'environnement :**
```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
./mvnw -q -o -DskipTests package
PID=$(powershell -Command "(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue).OwningProcess" | tr -d '\r' | head -1); [ -n "$PID" ] && powershell -Command "Stop-Process -Id $PID -Force"
set -a; source <(grep -E "^(DB_PASSWORD|DB_USER|DB_NAME|MINIO_ACCESS_KEY|MINIO_SECRET_KEY|MINIO_BUCKET|ENCRYPTION_KEY|JWT_PRIVATE_KEY|JWT_PUBLIC_KEY)=" .env); set +a
export DB_HOST=localhost DB_PORT=5433 MINIO_ENDPOINT=http://localhost:9000 MAIL_HOST=localhost MAIL_PORT=1025 SPRING_PROFILES_ACTIVE=dev APP_PORT=8080
java -jar target/invoice-system-1.0.0-SNAPSHOT.jar    # lancer en arrière-plan
# attendre le démarrage, puis :
curl -s http://localhost:8080/actuator/health          # attendu : {"status":"UP"}
cd frontend && npm run dev                             # port 3000 (arrière-plan)
```

**Contrôles runtime ciblés sur ce qui a été corrigé** (via Playwright MCP si dispo, sinon curl/API) :
1. **MFA i18n (ANO-005)** : interface MFA/lockout en FR → libellés traduits, pas de clé brute
   ni d'anglais. (Comptes MFA : `daf`, `rsi`, `dsi` ; OTP en dev = bypass possible.)
2. **Backups i18n (ANO-006)** : ouvrir `/admin/backups` en FR → libellés `Sauvegardes`,
   `Sauvegarde et restauration`, etc. (pas `backups.navTitle`).
3. **Date localisée (ANO-009)** : page `/payments` en FR → date au format `29/06/2026`
   (pas `6/29/2026`). Bascule EN → format US.
4. **En-têtes export (ANO-010)** : déclencher un export CSV/Excel depuis l'UI **française**
   (factures + suppliers + budget) → en-têtes en FR (`Référence, Fournisseur, Montant…`).
   Re-déclencher en EN → en-têtes EN.
5. **Endpoints supplier (ANO-011)** : confirmer que l'activation/suspension d'un fournisseur
   fonctionne toujours depuis l'UI admin (le verbe gardé doit matcher le front).
6. **Non-régression cœur** : login `aa`, `GET /invoices` (liste 200, colonne Département
   remplie — ANO-008), création facture (`POST /invoices` 201), `GET /invoices/{id}` (200).
   → c'étaient les bugs 🔴 ; confirmer qu'ils restent corrigés.

**Comptes test (mdp `Test1234!`) :** `aa` (ASSISTANT_COMPTABLE), `daf` (DAF/N1 Finance),
`rsi` (N1 INFO), `dsi` (N2 INFO), `supplier`. `admin` = autre mdp (inconnu).
**psql :** `/c/Program Files/PostgreSQL/17/bin/psql.exe`, `localhost:5433`, mdp dans `.env`.
**Dept IDs :** FIN=`cfca168e-f986-41d4-8d4e-9c00aed16d57`, INFO=`05cc38e9-b284-4fc0-bd7a-daefa735b0f2`.
**Uploads Playwright :** fichiers sous `c:\Users\Dany\Documents\FINAL PROJECT\.playwright-mcp\`.

---

## 3. SI TOUT EST VERT → COMMITS SÉPARÉS PAR THÈME (proposer, puis exécuter sur accord)

> Stratégie validée par le user : **one-commit-one-topic**. NE PAS mélanger le travail user
> (ArchiveFolder/Backup/V36→V40) avec les correctifs d'audit. Branche actuelle :
> `chore/sanitize-docs-migrations` (2 commits déjà non poussés).

**Avant de committer :** `git status` + `git diff --stat` pour cartographier précisément quels
fichiers appartiennent à quel thème. Proposer le découpage AU USER avant d'exécuter. Découpage suggéré :

- **Commit A — fixes 🔴 runtime de l'audit** (le cœur de la réfutation du « 100% PASS ») :
  - V38 (`purchase_order_items`), payload `departmentId` (`InvoiceCreatePage.tsx`),
    lazy mapping (`InvoiceService.java`, `Invoice.java`, `InvoiceRepository.java`,
    `InvoiceMapper.java`, `InvoiceDTO.java`).
  - ⚠ V38 fait partie du lot user (untracked) — **demander au user** s'il veut l'inclure ici
    ou le laisser avec son travail ArchiveFolder. NE PAS décider seul.
- **Commit B — i18n & localisation** : ANO-005 (`messages_fr.properties`), ANO-006 (`fr.json`),
  ANO-009 (`PaymentsPage.tsx`), ANO-010 (en-têtes export + clés i18n EN/FR).
- **Commit C — nettoyage / rationalisation** : ANO-011 (`SupplierController.java`, `docs/API.md`),
  ANO-012 (suppression logs + `.gitignore`).
- **Commit D — docs d'audit** : `docs/FINAL_QA_AUDIT_REPORT.md`, `docs/audit/*`,
  `docs/KNOWN_ISSUES_REGISTRY.md` (PROB-069→079), `docs/TASKS.md`.
- **Travail user (ArchiveFolder*, Backup*, ThreeWayMatchingLineResolution*, V36/V37/V39/V40)** :
  **NE PAS committer sans accord explicite** — c'est le travail du développeur ; lui demander
  comment il veut le gérer (commit séparé à part, ou il s'en occupe lui-même).

Format message : `type(scope): description` (cf. CLAUDE.md §11). Terminer chaque message par :
`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

**NE PAS `git push`** tant que le user ne l'a pas demandé. PR éventuelle = fin de lot, sur accord.

---

## 4. DOCUMENTATION (CLAUDE.md §12) — vérifier que c'est fait

S'assurer que `docs/KNOWN_ISSUES_REGISTRY.md` contient bien les entrées PROB-069→PROB-079
(ANO-001→012). Si Antigravity en a oublié, les compléter (cause racine / solution / règle
préventive / date). Mettre `docs/TASKS.md` à jour (statuts modules, open gaps).

---

## 5. RAPPORT FINAL ATTENDU (en français)

1. Tableau récap : chaque ANO (004→012) → statut vérifié (✅) + preuve (commande/runtime).
2. Sorties RÉELLES des commandes §2.1, §2.2 + résultats des contrôles runtime §2.3.
3. Découpage de commits proposé (§3) + confirmation d'attente d'accord avant commit/push.
4. Tout écart trouvé (un test qui casse, une régression runtime) → blocker report clair.
5. État du registre KNOWN_ISSUES + TASKS.

---

### Résumé en une ligne
Vérifie de bout en bout (builds + tests + runtime, preuves réelles) que les 4 fixes 🔴 et les
anomalies 🟠/🟡 (ANO-004→012) du système OCT invoice-system sont bien clos sans régression, en
respectant l'encodage Latin-1 du FR et la règle 0-échec ; puis propose des commits séparés par
thème (sans toucher au travail user sans accord) et attends le feu vert avant de committer/pousser.
