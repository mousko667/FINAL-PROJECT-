# Assainissement encodage `messages_fr.properties` — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconstruire `messages_fr.properties` en un fichier ASCII `\uXXXX` propre — supprimer le bloc MFA UTF-16 en doublon, réparer 31 clés aux accents détruits, nettoyer les BOM/mojibake — sans toucher aucune autre clé.

**Architecture :** Un unique script Python jetable (dans le scratchpad) manipule le fichier en mode **binaire** : il découpe le fichier en lignes sur `\r\n`, supprime les lignes du bloc UTF-16 (détectées par présence d'octets NUL), remplace les 31 lignes détruites par une table figée clé→valeur, corrige les cosmétiques, puis réécrit en ASCII strict + CRLF avec des `assert` de validation. Aucune écriture texte directe (l'outil Edit corrompt l'encodage de ce fichier).

**Tech Stack :** Python 3.14 (déjà installé), git (add sélectif), Maven wrapper (`./mvnw`) pour la vérif runtime backend.

## Global Constraints

- **Fichier cible unique :** `src/main/resources/i18n/messages_fr.properties`. Ne modifier AUCUN autre fichier (ni `messages_en.properties`, ni les ~25 fichiers déjà modifiés dans l'arbre — travail dev solo à préserver).
- **Encodage de sortie :** ASCII pur (0 octet > 127), 0 octet NUL, fins de ligne **CRLF** (`\r\n`).
- **Format i18n :** échappement `\uXXXX` majuscules hexa (ex. `é` pour é), cohérent avec le reste du fichier.
- **Écriture binaire only :** `open(path, "rb")` / `open(path, "wb")`. Jamais Edit/Write texte sur ce fichier.
- **Branche :** exécution sur `feat/i18n-fr-encoding` créée depuis `main` (isolée du reste). Le spec est déjà commité sur `feat/reports-pdf-metadata` (commit `8c719cc`) — ne pas y retoucher.
- **Table de vérité :** la table §5 du spec (`docs/superpowers/specs/2026-07-11-i18n-fr-encoding-sanitization-design.md`) — 31 clés. Recopiée verbatim dans la Task 2.
- **Règle projet [No failures on task completion] :** la tâche n'est « done » qu'avec `./mvnw test` à 0 échec ET vérif runtime OK.

---

### Task 0: Créer la branche de travail isolée

**Files:**
- Aucun fichier modifié (opération git).

**Interfaces:**
- Consumes: `main` (base propre du repo).
- Produces: branche `feat/i18n-fr-encoding` active, arbre de travail conservé.

⚠️ L'arbre contient ~25 fichiers modifiés non commités (travail dev solo). On NE les stashe PAS et on NE les commite PAS. On crée simplement une branche qui les emporte tels quels ; seul `messages_fr.properties` sera ajouté au commit final (add sélectif). C'est validé avec l'utilisateur (option « i18n sur branche dédiée »).

- [ ] **Step 1: Vérifier l'état de départ**

Run: `git rev-parse --abbrev-ref HEAD && git status --short | wc -l`
Expected: affiche la branche courante (`feat/reports-pdf-metadata`) et un nombre de fichiers modifiés (~25).

- [ ] **Step 2: Créer et basculer sur la branche i18n depuis main**

⚠️ On branche depuis `main` pour respecter [git-one-topic-per-branch], mais comme l'arbre de travail est partagé, `git checkout -b` conserve les modifs non commitées. Si git refuse (conflit de checkout), NE PAS forcer — émettre un blocker et demander à l'utilisateur.

Run:
```bash
git checkout main
git checkout -b feat/i18n-fr-encoding
```
Expected: `Switched to a new branch 'feat/i18n-fr-encoding'`, aucune modif perdue.

- [ ] **Step 3: Confirmer que messages_fr est toujours corrompu (état de départ attendu)**

Run: `python -c "print(open('src/main/resources/i18n/messages_fr.properties','rb').read().count(b'\x00'))"`
Expected: `1287` (corruption UTF-16 intacte, point de départ).

*(Pas de commit à cette étape — c'est du setup git.)*

---

### Task 1: Extraire et geler l'état de référence (garde-fous)

**Files:**
- Create: `<scratchpad>/i18n_baseline.py` (script de capture, jetable)

**Interfaces:**
- Consumes: `messages_fr.properties` (corrompu), `messages_en.properties` (sain).
- Produces: deux ensembles de clés servant d'invariants à la Task 3 :
  - `FR_KEYS_BEFORE` : set des clés FR d'origine (avec doublons comptés).
  - `EN_KEYS` : set des clés EN (parité cible).
  - Constante `MFA_KEYS` (15) et `DAMAGED_KEYS` (31) — listes figées.

Le `<scratchpad>` = `C:\Users\Dany\AppData\Local\Temp\claude\c--Users-Dany-Documents-FINAL-PROJECT\1b407eb8-c05b-46d5-aa33-213fd508c908\scratchpad`.

- [ ] **Step 1: Écrire le script de capture de référence**

Create `<scratchpad>/i18n_baseline.py`:
```python
import sys, json
sys.stdout.reconfigure(encoding="utf-8")
from collections import Counter

FR = "src/main/resources/i18n/messages_fr.properties"
EN = "src/main/resources/i18n/messages_en.properties"

def keys(path, enc="latin-1"):
    out = []
    raw = open(path, "rb").read()
    for b in raw.split(b"\r\n"):
        t = b.decode(enc, "replace")
        if "=" in t and not t.lstrip().startswith("#"):
            out.append(t.split("=", 1)[0].strip())
    return out

fr = keys(FR)
en = keys(EN, "utf-8")
fr_counter = Counter(fr)
dupes = {k: c for k, c in fr_counter.items() if c > 1}

MFA_KEYS = ["mfa.setup.required","mfa.setup.start","mfa.qr.generate","mfa.qr.manual_entry",
 "mfa.qr.backup_codes","mfa.verification.enter_code","mfa.verification.invalid",
 "mfa.enabled.success","mfa.already_enabled","mfa.confirm.success","error.otp.invalid",
 "error.otp.expired","error.account.locked","error.login.attempts_exceeded","action.unlock.success"]

result = {
    "fr_unique_keys": sorted(set(fr)),
    "fr_total": len(fr),
    "fr_unique": len(set(fr)),
    "dupes": dupes,
    "en_keys": sorted(set(en)),
    "mfa_keys": MFA_KEYS,
}
open(r"<scratchpad>/i18n_baseline.json", "w", encoding="utf-8").write(json.dumps(result, indent=2, ensure_ascii=False))
print("fr_total:", result["fr_total"], "fr_unique:", result["fr_unique"])
print("dupes:", list(dupes.keys()))
print("en_keys:", len(result["en_keys"]))
```
(Remplacer `<scratchpad>` par le chemin absolu réel.)

- [ ] **Step 2: Exécuter la capture**

Run: `python <scratchpad>/i18n_baseline.py`
Expected:
```
fr_total: 273 fr_unique: 258
dupes: [ ... les 15 clés mfa/otp/account/login/unlock ... ]
en_keys: (nombre ≈ 250+)
```
→ confirme : 15 doublons = exactement les clés MFA. Après nettoyage, `fr_unique` doit rester 258 et `fr_total` doit devenir 258 (0 doublon).

*(Pas de commit — génération d'artefacts de référence dans le scratchpad.)*

---

### Task 2: Écrire le script de reconstruction

**Files:**
- Create: `<scratchpad>/rebuild_fr.py` (script de reconstruction, jetable)

**Interfaces:**
- Consumes: `messages_fr.properties` (corrompu), la table 31-clés (ci-dessous).
- Produces: le script prêt à exécuter (n'écrit rien encore — l'écriture + validation sont dans Task 3). Fonctions clés :
  - `esc(s: str) -> str` : échappe tout caractère > 127 en `\uXXXX`.
  - `REPAIRS: dict[str,str]` : 31 entrées clé → valeur FR corrigée (texte brut Unicode, pas encore échappé).

- [ ] **Step 1: Écrire le script de reconstruction**

Create `<scratchpad>/rebuild_fr.py`:
```python
import sys, re
from collections import Counter
sys.stdout.reconfigure(encoding="utf-8")

PATH = "src/main/resources/i18n/messages_fr.properties"

# ---- table de réparation (31 clés) : source = spec §5, verbatim ----
REPAIRS = {
    "payment.recorded.success": "Paiement enregistré avec succès",
    "remittance.invoice.ref": "Référence de Facture",
    "remittance.amount": "Montant Payé",
    "remittance.payment_method": "Méthode de Paiement",
    "remittance.reference": "Numéro de Référence",
    "remittance.download.url.generated": "URL de téléchargement de l'avis de paiement générée avec succès",
    "report.aging.title": "Rapport d'Analyse de l'Âge d'Impayé",
    "report.aging.total_overdue": "Total Impayé",
    "report.cashflow.title": "Projection de Flux de Trésorerie",
    "report.cashflow.projected_amount": "Montant Projeté",
    "report.cashflow.total_projected": "Montant Total Projeté",
    "report.supplier.payment_method": "Méthode de Paiement",
    "webhook.delivery.failed": "Échec de la livraison du webhook",
    "integration.status.title": "État de Santé de l'Intégration",
    "report.bottleneck.title": "Goulets d'étranglement d'approbation",
    "report.bottleneck.department": "Département",
    "report.bottleneck.step_order": "Ordre d'étape",
    "report.bottleneck.step_name": "Nom d'étape",
    "report.bottleneck.step_count": "Nombre d'étapes",
    "report.supplier.performance.accuracy_rate": "Taux de précision des factures",
    "report.kpi.webhook_delivery_success_rate": "Taux de succès de livraison Webhook",
    "supplier.dashboard.matching_status_breakdown": "Répartition du statut de concordance",
    "supplier.dashboard.next_expected_payment_date": "Prochaine date de paiement prévue",
    "invoice.reject.code.PIECE_MANQUANTE": "Pièce justificative manquante",
    "invoice.reject.code.HORS_BUDGET": "Dépense hors budget",
    "error.reject.detail.required.for.other": 'Un détail d\'au moins 10 caractères est requis lorsque le motif est "Autre".',
    "error.escalation_rule.not_found": "Règle d'escalade introuvable",
    "escalation_rule.created": "Règle d'escalade créée",
    "escalation_rule.updated": "Règle d'escalade mise à jour",
    "escalation_rule.deleted": "Règle d'escalade supprimée",
    "retention_policy.updated": "Politique de rétention mise à jour",
}
assert len(REPAIRS) == 31, f"attendu 31 clés, obtenu {len(REPAIRS)}"

def esc(s: str) -> str:
    """ASCII passthrough; tout caractère non-ASCII -> \\uXXXX (majuscules)."""
    return "".join(ch if ord(ch) < 128 else "\\u%04X" % ord(ch) for ch in s)

def line_is_utf16(b: bytes) -> bool:
    """Une ligne du bloc MFA UTF-16 contient des octets NUL."""
    return b"\x00" in b

def build():
    raw = open(PATH, "rb").read()
    lines = raw.split(b"\r\n")
    out = []
    for b in lines:
        # Passe A : supprimer toute ligne appartenant au bloc UTF-16 (contient NUL),
        # y compris son commentaire "# Authentification..." également en UTF-16.
        if line_is_utf16(b):
            continue
        t = b.decode("latin-1")  # préserve chaque octet
        # Passe C : cosmétique ligne 1 (mojibake e2 3f 22)
        if "\xe2?\" Messages FR" in t:
            t = t.replace("\xe2?\" Messages FR", "\\u2014 Messages FR")
        # Passe C : BOM parasite "﻿ : {0}"
        if "\\uFEFF : {0}" in t:
            t = t.replace("\\uFEFF : {0}", " : {0}")
        # Passe B : réparer les clés détruites
        if "=" in t and not t.lstrip().startswith("#"):
            k = t.split("=", 1)[0].strip()
            if k in REPAIRS:
                t = "%s=%s" % (k, esc(REPAIRS[k]))
        out.append(t)
    result = "\r\n".join(out)
    return result.encode("ascii")  # échoue si un octet > 127 subsiste

if __name__ == "__main__":
    data = build()
    # rapport à sec (dry-run) : n'écrit PAS encore
    print("bytes:", len(data))
    print("NUL:", data.count(b"\x00"))
    print("non-ascii:", sum(1 for x in data if x > 127))
    txt = data.decode("ascii")
    keys = [l.split("=",1)[0].strip() for l in txt.split("\r\n") if "=" in l and not l.lstrip().startswith("#")]
    c = Counter(keys)
    print("dupes restants:", {k:v for k,v in c.items() if v>1})
    print("total keys:", len(keys), "uniques:", len(set(keys)))
```
(Remplacer `<scratchpad>` implicite ; le script lit le chemin relatif `PATH` depuis la racine repo.)

- [ ] **Step 2: Dry-run — vérifier la reconstruction SANS écrire**

Run: `python <scratchpad>/rebuild_fr.py`
Expected:
```
bytes: (≈ 16000-17000)
NUL: 0
non-ascii: 0
dupes restants: {}
total keys: 258   uniques: 258
```
→ Si `non-ascii` ≠ 0 : un caractère n'a pas été échappé → corriger `esc`/`REPAIRS`. Si `dupes restants` ≠ {} : le bloc UTF-16 n'a pas été entièrement supprimé → inspecter. Si `.encode("ascii")` lève `UnicodeEncodeError` : un octet brut > 127 subsiste dans une ligne non réparée → l'identifier et l'ajouter à REPAIRS ou aux cosmétiques.

*(Pas de commit — le script ne fait qu'un dry-run.)*

---

### Task 3: Exécuter la reconstruction + valider + commiter

**Files:**
- Modify: `src/main/resources/i18n/messages_fr.properties` (réécriture binaire)
- Create: `<scratchpad>/apply_and_validate.py` (écrit le fichier + valide contre le baseline)

**Interfaces:**
- Consumes: `rebuild_fr.build()` (Task 2), `i18n_baseline.json` (Task 1).
- Produces: fichier `messages_fr.properties` assaini + commit.

- [ ] **Step 1: Écrire le script apply+validate**

Create `<scratchpad>/apply_and_validate.py`:
```python
import sys, json, importlib.util, codecs
sys.stdout.reconfigure(encoding="utf-8")

# importer build() depuis rebuild_fr.py
spec = importlib.util.spec_from_file_location("rebuild_fr", r"<scratchpad>/rebuild_fr.py")
mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)

PATH = "src/main/resources/i18n/messages_fr.properties"
baseline = json.load(open(r"<scratchpad>/i18n_baseline.json", encoding="utf-8"))

data = mod.build()

# --- validations bloquantes AVANT écriture ---
assert data.count(b"\x00") == 0, "octets NUL restants!"
assert all(x < 128 for x in data), "octets non-ASCII restants!"
txt = data.decode("ascii")
lines = txt.split("\r\n")
keys = [l.split("=",1)[0].strip() for l in lines if "=" in l and not l.lstrip().startswith("#")]
from collections import Counter
c = Counter(keys)
assert not [k for k,v in c.items() if v>1], f"doublons: {[k for k,v in c.items() if v>1]}"

# parité : aucune clé perdue (sauf les 14 doublons MFA supprimés -> uniques inchangés)
before_unique = set(baseline["fr_unique_keys"])
after_unique = set(keys)
lost = before_unique - after_unique
assert not lost, f"clés perdues: {lost}"

# les 31 clés réparées se dé-échappent en accents corrects (pas de U+FFFD)
kv = {l.split("=",1)[0].strip(): l.split("=",1)[1] for l in lines if "=" in l and not l.lstrip().startswith("#")}
for k in mod.REPAIRS:
    val = codecs.decode(kv[k].encode("latin-1","backslashreplace"), "unicode_escape") if "\\u" in kv[k] else kv[k]
    assert "�" not in val, f"{k} contient encore U+FFFD"
    assert val == mod.REPAIRS[k], f"{k}: attendu {mod.REPAIRS[k]!r}, obtenu {val!r}"

# --- écriture binaire ---
open(PATH, "wb").write(data)
print("OK — écrit", len(data), "octets ; 0 NUL ; 0 non-ascii ; 0 doublon ;", len(keys), "clés")
```
(Remplacer les deux `<scratchpad>` par le chemin absolu réel.)

- [ ] **Step 2: Exécuter apply+validate**

Run: `python <scratchpad>/apply_and_validate.py`
Expected: `OK — écrit NNNNN octets ; 0 NUL ; 0 non-ascii ; 0 doublon ; 258 clés`
→ Si un `assert` échoue : NE PAS commiter, corriger la cause (REPAIRS/esc), relancer.

- [ ] **Step 3: Vérifier l'intégrité binaire du fichier écrit**

Run:
```bash
python -c "raw=open('src/main/resources/i18n/messages_fr.properties','rb').read(); print('NUL',raw.count(b'\x00'),'nonascii',sum(1 for b in raw if b>127),'CR',raw.count(b'\r'),'LF',raw.count(b'\n'))"
```
Expected: `NUL 0 nonascii 0 CR <n> LF <n>` avec `CR == LF` (toutes les lignes en CRLF).

- [ ] **Step 4: Contrôle visuel du bloc MFA restant + 3 clés réparées**

Run:
```bash
grep -nE "^(mfa\.setup\.start|webhook\.delivery\.failed|report\.aging\.title)=" src/main/resources/i18n/messages_fr.properties
grep -c "mfa.setup.start" src/main/resources/i18n/messages_fr.properties
```
Expected: chaque clé apparaît **une seule fois** ; `mfa.setup.start` compté = `1` ; les valeurs montrent des `\uXXXX` propres (ex. `Rapport d'Analyse de l'Âge d'Impayé`).

- [ ] **Step 5: Compiler le backend (le fichier de ressources est valide)**

Run: `./mvnw -q -o compile 2>&1 | tail -5 || ./mvnw -q compile 2>&1 | tail -5`
Expected: `BUILD SUCCESS` (ou compilation sans erreur). Le fichier `.properties` étant une ressource, une erreur de parse n'apparaît qu'au runtime — d'où la Task 4.

- [ ] **Step 6: Commit (add sélectif — SEULEMENT le fichier i18n)**

⚠️ `git add` UNIQUEMENT le fichier cible pour ne pas emporter les ~25 modifs dev solo.

Run:
```bash
git add src/main/resources/i18n/messages_fr.properties
git commit -m "$(printf 'fix(i18n): assainit encodage messages_fr (UTF-16 + accents detruits)\n\nSupprime le bloc MFA UTF-16 en doublon (code mort), repare 31 cles aux\naccents U+FFFD via la table spec/EN, retire 6 BOM parasites + mojibake.\nFichier desormais ASCII \\\\uXXXX pur, 0 octet NUL, 0 doublon (258 cles).\n\nCo-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>')"
```
Expected: `1 file changed`. Vérifier ensuite `git show --stat HEAD` = 1 seul fichier.

---

### Task 4: Vérification runtime (la tâche n'est done qu'ici)

**Files:**
- Aucun fichier modifié (vérification comportementale).

**Interfaces:**
- Consumes: backend démarré avec le nouveau fichier i18n.
- Produces: preuve runtime que les messages FR s'affichent correctement.

Conforme à [verify-runtime-not-snapshot] et [No failures on task completion] : un fichier au parse correct peut quand même mal s'afficher — on vérifie le vrai rendu.

- [ ] **Step 1: Lancer la suite de tests backend (aucune régression)**

Run: `./mvnw test 2>&1 | tail -20`
Expected: `BUILD SUCCESS`, 0 failure / 0 error. Si un test charge les messages et échoue → investiguer (règle projet : pas de « pré-existant » toléré hors plan).

- [ ] **Step 2: Démarrer le backend et déclencher un message FR réparé**

Déployer selon la procédure CLAUDE.md §13 (docker) OU lancer en local. Puis provoquer une erreur dont le message est une clé réparée, en `Accept-Language: fr`. Exemple simple via un endpoint qui renvoie `error.escalation_rule.not_found` ou en lisant un message MFA.

Run (exemple, à adapter à un endpoint réel renvoyant une clé réparée) :
```bash
curl -s -H "Accept-Language: fr" http://localhost:8080/api/... | grep -o "message[^,]*"
```
Expected: le message contient des accents corrects (« Règle d'escalade introuvable », « Échec… ») — **aucun** `�`, aucun caractère espacé.

- [ ] **Step 3: Contrôle MFA (bloc dédoublonné)**

Vérifier qu'un message MFA (ex. `mfa.setup.required`) s'affiche avec la traduction canonique « double authentification » et des accents corrects. Confirme que le bon bloc (le sain) a survécu au dédoublonnage.

Expected: « La configuration de la double authentification… » lisible, accents OK.

- [ ] **Step 4: Journaliser dans KNOWN_ISSUES_REGISTRY (règle CLAUDE.md §12)**

Ajouter une entrée `PROB-NNN` (prochain numéro libre) à `docs/KNOWN_ISSUES_REGISTRY.md` :
- **Cause racine :** copier-coller d'un bloc MFA en UTF-16LE dans un `.properties` lu en UTF-8 + double bloc MFA (dernier gagne) + accents antérieurs déjà écrasés en U+FFFD par des ré-encodages successifs.
- **Solution :** reconstruction binaire → ASCII `\uXXXX` + CRLF ; dédoublonnage ; réparation 31 clés via réf. EN.
- **Règle préventive :** i18n FR toujours en `\uXXXX` ASCII ; ne jamais éditer ce fichier via un outil qui normalise l'encodage (Edit) ; vérifier `count(b"\x00")==0` et `all(b<128)` avant tout commit du fichier.

Run:
```bash
git add docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "$(printf 'docs(issues): PROB-NNN messages_fr encodage UTF-16 + accents detruits\n\nCo-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>')"
```
Expected: `1 file changed`.

- [ ] **Step 5: Émettre le prompt de reprise (cadence [per-task-commit-and-handoff])**

Produire un bloc paste-ready résumant : branche `feat/i18n-fr-encoding`, commits i18n + registry, état (assaini/vérifié), et prochaine étape = **décision merge/push avec l'utilisateur** puis **retour au design system** (spec fondation à écrire).

---

## Self-Review (effectuée)

**1. Couverture spec :** §2A dédoublonnage MFA → Task 3 passe A. §2B 31 clés → Task 2 REPAIRS + Task 3 validation. §2C cosmétiques → Task 3 passes C. §2D intégrité → Task 3 Steps 2-3. §4 contrainte binaire → Global Constraints + scripts. §5 table 31 → Task 2 verbatim. §6 vérif → Task 4. §7 livrables (registry, TASKS) → Task 4 Step 4. ✔ Toutes couvertes.

**2. Placeholders :** `PROB-NNN` est un numéro à résoudre au moment de l'écriture (le registre donne le prochain libre) — acceptable, pas un TBD de contenu. Les endpoints curl de Task 4 Step 2 sont « à adapter » car dépendants du backend réel — le critère de succès (accents corrects, pas de �) est explicite. Pas de placeholder de contenu bloquant.

**3. Cohérence des types :** `build()` défini Task 2, consommé Task 3. `REPAIRS`/`esc()` cohérents. `i18n_baseline.json` produit Task 1, lu Task 3. Compteurs `258 clés` cohérents partout (273 total − 15 doublons MFA + les uniques restent 258). ✔
