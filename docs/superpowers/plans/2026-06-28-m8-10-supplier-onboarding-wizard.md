# M8 #10 - Assistant d'onboarding fournisseur multi-etapes - Implementation Plan

> **Goal:** Ajouter un onboarding fournisseur multi-etapes cote admin, avec un
> assistant front qui guide la creation/verification et une garde backend simple
> avant activation.

> **Architecture:** React stepper en 3 etapes sur `/admin/suppliers/new/onboarding`
> et page detail fournisseur enrichie pour suivre la progression. Cote backend,
> activation bloquee si le dossier minimal est incomplet, sans changer le modele
> de donnees ni ajouter de migration.

> **Gates:** `./mvnw.cmd test` + `npm.cmd run test -- --run` + `npx.cmd tsc --noEmit`
> avant le commit final.

---

## Task 1 - Cadrage front et route d'onboarding

**Files**
- Modify: `frontend/src/AppRoutes.tsx`
- Create: `frontend/src/pages/admin/SupplierOnboardingPage.tsx`
- Modify: `frontend/src/pages/admin/SupplierFormPage.tsx`

**Goal**
- Ajouter une route admin dediee a l'onboarding et rendre le formulaire
  existant reutilisable comme premiere etape du wizard.

**Tests**
- Ajouter un test front qui verifie que la route onboarding est montable et
  que la navigation de base fonctionne.

---

## Task 2 - Wizard frontend en 3 etapes

**Files**
- Create: `frontend/src/components/supplier/SupplierOnboardingStepper.tsx`
- Create: `frontend/src/components/supplier/SupplierOnboardingChecklist.tsx`
- Modify: `frontend/src/pages/admin/SupplierOnboardingPage.tsx`
- Modify: `frontend/src/i18n/fr.json`
- Modify: `frontend/src/i18n/en.json`

**Goal**
- Construire le parcours multi-etapes avec resume final et checklist visuelle.

**Tests**
- Navigation stepper, resume final, et charge utile de creation/maj.

---

## Task 3 - Garde backend avant activation

**Files**
- Modify: `src/main/java/com/oct/invoicesystem/domain/supplier/service/SupplierServiceImpl.java`
- Modify: `src/main/resources/i18n/messages_fr.properties`
- Modify: `src/main/resources/i18n/messages_en.properties`

**Goal**
- Bloquer `activateSupplier` si le dossier minimal est incomplet et exposer un
  message i18n clair.

**Tests**
- Happy path activation, dossier incomplet, supplier not found.

---

## Task 4 - Tests backend et integration

**Files**
- Modify/Create: `src/test/java/com/oct/invoicesystem/domain/supplier/service/SupplierServiceTest.java`
- Modify/Create: `src/test/java/com/oct/invoicesystem/domain/supplier/controller/SupplierIntegrationTest.java`

**Goal**
- Couvrir la nouvelle garde et le flux admin avec les bons roles.

**Tests**
- Service happy + 2 edge cases.
- Endpoint activate: 200 admin/assistant, 403 role interdit.

---

## Task 5 - Finalisation documentaire

**Files**
- Modify: `docs/TASKS.md`
- Modify: `docs/MEMORY.md`

**Goal**
- Marquer M8 #10 comme resolu, noter la session et garder la roadmap alignee.

**Tests**
- Gate final complet sur front, puis commit atomique.

