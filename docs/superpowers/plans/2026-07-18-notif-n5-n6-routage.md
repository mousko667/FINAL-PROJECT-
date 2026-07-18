# Correctif N5 + N6 — routage des notifications rejet / BAP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Router les notifications de rejet et de Bon à Payer vers le(s) bon(s) Assistant(s) Comptable(s) et notifier le fournisseur à l'approbation (BAP), au lieu de cibler aveuglément `invoice.getSubmittedBy()`.

**Architecture:** Deux listeners d'événements (`EmailNotificationListener`, `PersistNotificationListener`) écoutent `InvoiceRejectedEvent` et `BonAPayerEvent`. On introduit dans chacun un helper `resolveAccountingRecipients(Invoice)` qui retourne `[submitter]` si le submitter porte `ROLE_ASSISTANT_COMPTABLE` (facture interne), sinon la liste des AA actifs (facture portail où submitter = fournisseur). On ajoute la notification fournisseur manquante sur BAP (email nouveau template + in-app), et l'in-app fournisseur manquante sur rejet.

**Tech Stack:** Spring Boot 3.4 · Java 21 · Spring event listeners `@Async @EventListener` · Mockito/JUnit 5 · Thymeleaf (templates email).

## Global Constraints

- Devise = **XAF** (jamais XOF).
- **Aucune migration**, aucun changement de state-machine.
- **Aucune nouvelle clé i18n** `messages_*.properties` : sujets de mail et titres in-app restent **bilingues en dur** (chaînes FR/EN concaténées), dans le style existant des deux listeners.
- Le rôle est testé via le contrat `getAuthorities()` de `User` : `user.getAuthorities().stream().anyMatch(a -> "ROLE_ASSISTANT_COMPTABLE".equals(a.getAuthority()))`. Nom de rôle exact : `ROLE_ASSISTANT_COMPTABLE`.
- Méthodes repo existantes réutilisées telles quelles : `userRepository.findActiveUsersByRoleName(String)`, `userRepository.findActiveUsersBySupplierId(UUID)`.
- Gate : `./mvnw test` depuis un `target/surefire-reports` vide → **614 baseline + nouveaux tests, 0 failure / 0 error / 0 skipped**. Env pour l'intégration : `export DB_NAME=oct_invoice DB_USER=postgres DB_PASSWORD=dany`.
- Living-doc obligatoire : `docs/KNOWN_ISSUES_REGISTRY.md` (PROB-119 N5 + PROB-120 N6, **via heredoc bash** — le fichier contient un octet NUL, jamais Edit) + `docs/TASKS.md`.
- Fixture rôle en test (pattern déjà utilisé dans `InvoiceServiceTest`) :
  ```java
  user.setUserRoles(java.util.Set.of(
      com.oct.invoicesystem.domain.user.model.UserRole.builder()
          .role(com.oct.invoicesystem.domain.user.model.Role.builder().name("ROLE_ASSISTANT_COMPTABLE").build())
          .user(user)
          .build()));
  ```

---

## File Structure

- `src/main/java/com/oct/invoicesystem/domain/notification/event/listener/EmailNotificationListener.java` — MODIFIER : helper `resolveAccountingRecipients` + réécrire `onInvoiceRejected` / `onBonAPayer`.
- `src/main/java/com/oct/invoicesystem/domain/notification/event/listener/PersistNotificationListener.java` — MODIFIER : helper `resolveAccountingRecipients` + réécrire `onInvoiceRejected` / `onBonAPayer` + in-app fournisseur.
- `src/main/resources/templates/email/supplier-invoice-approved.html` — CRÉER (calqué sur `supplier-invoice-rejected.html`).
- `src/test/java/com/oct/invoicesystem/domain/notification/event/listener/EmailNotificationListenerTest.java` — MODIFIER : mettre à jour 2 tests existants + ajouter les cas N5/N6.
- `src/test/java/com/oct/invoicesystem/domain/notification/event/listener/PersistNotificationListenerTest.java` — MODIFIER : idem.

> Note d'ordonnancement TDD : les 2 tests existants (`onInvoiceRejected_sendsEmailToSubmitter`, `onBonAPayer_sendsEmailToSubmitter`) supposent aujourd'hui que `submittedBy` (sans rôle) reçoit le mail interne. Après correctif, un submitter **sans rôle** tombe dans la branche « facture portail » → mock `findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")` retourne liste vide → plus d'email interne. Ces deux tests doivent donc être **transformés en cas « facture interne » (submitter = AA)** dans la même tâche que le code, sinon ils cassent. C'est prévu explicitement ci-dessous.

---

## Task 1 : Helper `resolveAccountingRecipients` + routage email (N5) sur rejet et BAP, template BAP fournisseur (N6)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/notification/event/listener/EmailNotificationListener.java` (lignes 85-117 pour les deux handlers ; ajout d'un helper dans la section Helpers)
- Create: `src/main/resources/templates/email/supplier-invoice-approved.html`
- Test: `src/test/java/com/oct/invoicesystem/domain/notification/event/listener/EmailNotificationListenerTest.java`

**Interfaces:**
- Consumes : `userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE") : List<User>`, `User.getAuthorities()`, helpers privés existants `notifyUsers(List<User>, String, String, Map)`, `notifySupplier(Invoice, String, String, Map)`, `buildCommonVariables(Invoice) : Map<String,Object>`.
- Produces : helper privé `List<User> resolveAccountingRecipients(Invoice invoice)`. (Signature réutilisée à l'identique dans PersistNotificationListener — Task 2.)

- [ ] **Step 1 : Réécrire les 2 tests existants en « facture interne » + ajouter les cas N5/N6**

Remplacer intégralement le contenu de `EmailNotificationListenerTest.java` par :

```java
package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationListenerTest {

    @Mock EmailService emailService;
    @Mock InvoiceRepository invoiceRepository;
    @Mock UserRepository userRepository;
    @Mock SupplierRepository supplierRepository;

    @InjectMocks
    EmailNotificationListener listener;

    private Invoice invoice;
    private User n1User;
    private User aaSubmitter;

    private static User withRole(User u, String roleName) {
        u.setUserRoles(Set.of(UserRole.builder()
                .role(Role.builder().name(roleName).build())
                .user(u)
                .build()));
        return u;
    }

    @BeforeEach
    void setUp() {
        Department dept = new Department();
        dept.setCode("DRH");
        dept.setN1Role("ROLE_VALIDATEUR_N1_DRH");
        dept.setRequiresN2(false);

        aaSubmitter = withRole(User.builder()
                .id(UUID.randomUUID())
                .username("assistant")
                .email("assistant@oct.ga")
                .password("x").firstName("A").lastName("B")
                .build(), "ROLE_ASSISTANT_COMPTABLE");

        n1User = User.builder()
                .id(UUID.randomUUID())
                .username("n1")
                .email("n1@oct.ga")
                .password("x").firstName("N").lastName("One")
                .build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00001")
                .supplierName("Acme")
                .supplierEmail("supplier@acme.ga")
                .amount(BigDecimal.valueOf(100_000))
                .currency("XAF")
                .department(dept)
                .submittedBy(aaSubmitter)
                .build();
    }

    @Test
    void onInvoiceSubmitted_sendsEmailToN1Approvers() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_VALIDATEUR_N1_DRH")).thenReturn(List.of(n1User));

        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, invoice.getId()));

        verify(emailService).sendEmailToUsers(eq(List.of("n1@oct.ga")), anyString(), anyString(), anyMap());
    }

    @Test
    void onInvoiceSubmitted_doesNothing_whenInvoiceNotFound() {
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());
        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, UUID.randomUUID()));
        verifyNoInteractions(emailService);
    }

    // ── N5 : rejet, facture INTERNE (submitter = AA) → cet AA seul + fournisseur ──
    @Test
    void onInvoiceRejected_internalInvoice_sendsToSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Missing docs"));

        // AA interne (le submitter lui-même), pas d'appel à findActiveUsersByRoleName
        verify(emailService).sendEmailToUsers(eq(List.of("assistant@oct.ga")), anyString(), eq("invoice-rejected"), anyMap());
        verify(userRepository, never()).findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");
        // fournisseur
        verify(emailService).sendEmailToUsers(eq(List.of("supplier@acme.ga")), anyString(), eq("supplier-invoice-rejected"), anyMap());
    }

    // ── N5 : rejet, facture PORTAIL (submitter = fournisseur, non-AA) → AA résolus + fournisseur ──
    @Test
    void onInvoiceRejected_portalInvoice_resolvesActiveAAs() {
        User portalSubmitter = User.builder()
                .id(UUID.randomUUID()).username("supplier").email("supplier@acme.ga")
                .password("x").firstName("S").lastName("Up").build(); // aucun rôle AA
        invoice.setSubmittedBy(portalSubmitter);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Missing docs"));

        // interne = AA actifs, PAS le fournisseur, via le template interne
        verify(emailService).sendEmailToUsers(eq(List.of("assistant@oct.ga")), anyString(), eq("invoice-rejected"), anyMap());
        // fournisseur reçoit SON template (et pas le template interne)
        verify(emailService).sendEmailToUsers(eq(List.of("supplier@acme.ga")), anyString(), eq("supplier-invoice-rejected"), anyMap());
    }

    // ── N6 : BAP, facture INTERNE → AA seul (template interne) + fournisseur (nouveau template) ──
    @Test
    void onBonAPayer_internalInvoice_notifiesSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        verify(emailService).sendEmailToUsers(eq(List.of("assistant@oct.ga")), anyString(), eq("invoice-approved"), anyMap());
        verify(emailService).sendEmailToUsers(eq(List.of("supplier@acme.ga")), anyString(), eq("supplier-invoice-approved"), anyMap());
    }

    // ── N5+N6 : BAP, facture PORTAIL → AA résolus + fournisseur ──
    @Test
    void onBonAPayer_portalInvoice_resolvesAAsAndNotifiesSupplier() {
        User portalSubmitter = User.builder()
                .id(UUID.randomUUID()).username("supplier").email("supplier@acme.ga")
                .password("x").firstName("S").lastName("Up").build();
        invoice.setSubmittedBy(portalSubmitter);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        verify(emailService).sendEmailToUsers(eq(List.of("assistant@oct.ga")), anyString(), eq("invoice-approved"), anyMap());
        verify(emailService).sendEmailToUsers(eq(List.of("supplier@acme.ga")), anyString(), eq("supplier-invoice-approved"), anyMap());
    }

    @Test
    void onInvoiceValidated_N1_singleLevel_sendsEmailToDAF() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        User daf = User.builder().id(UUID.randomUUID()).email("daf@oct.ga").username("daf").password("x").firstName("D").lastName("AF").build();
        when(userRepository.findActiveUsersByRoleName("ROLE_DAF")).thenReturn(List.of(daf));

        listener.onInvoiceValidated(new InvoiceValidatedEvent(this, invoice.getId(), "N1"));

        verify(emailService).sendEmailToUsers(eq(List.of("daf@oct.ga")), anyString(), anyString(), anyMap());
    }
}
```

- [ ] **Step 2 : Lancer les tests → vérifier qu'ils échouent (rouge)**

Run : `./mvnw test -Dtest=EmailNotificationListenerTest -q`
Attendu : ÉCHEC. `onBonAPayer_*` échouent (template `supplier-invoice-approved` jamais envoyé, notifySupplier absent sur BAP), `onInvoiceRejected_portalInvoice_resolvesActiveAAs` échoue (le code cible encore `submittedBy`).

- [ ] **Step 3 : Ajouter le helper `resolveAccountingRecipients`**

Dans `EmailNotificationListener.java`, section `// ── Helpers ──` (après la ligne 157), insérer :

```java
    /**
     * Résout le(s) destinataire(s) interne(s) « Assistant Comptable » d'une facture.
     * Si le soumetteur porte {@code ROLE_ASSISTANT_COMPTABLE} (facture interne saisie par un AA),
     * il est seul destinataire — il suit son propre dossier. Sinon (facture portail : le soumetteur
     * est le compte fournisseur), on cible tous les AA actifs.
     */
    private List<User> resolveAccountingRecipients(Invoice invoice) {
        User submitter = invoice.getSubmittedBy();
        if (submitter != null && submitter.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ASSISTANT_COMPTABLE".equals(a.getAuthority()))) {
            return List.of(submitter);
        }
        return userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");
    }
```

- [ ] **Step 4 : Réécrire `onInvoiceRejected` (lignes 85-102)**

Remplacer le corps par :

```java
    @Async
    @EventListener
    public void onInvoiceRejected(InvoiceRejectedEvent event) {
        log.info("Handling InvoiceRejectedEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            Map<String, Object> vars = buildCommonVariables(invoice);
            vars.put("reason", event.getReason());

            // Notify the resolved Assistant(s) Comptable (N5)
            List<User> accounting = resolveAccountingRecipients(invoice);
            if (!accounting.isEmpty()) {
                notifyUsers(accounting, "Facture rejetée / Invoice rejected", "invoice-rejected", vars);
            }

            // Notify supplier
            notifySupplier(invoice, "Votre facture a été rejetée / Your invoice was rejected", "supplier-invoice-rejected", vars);
        });
    }
```

- [ ] **Step 5 : Réécrire `onBonAPayer` (lignes 106-117)**

Remplacer le corps par :

```java
    @Async
    @EventListener
    public void onBonAPayer(BonAPayerEvent event) {
        log.info("Handling BonAPayerEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            Map<String, Object> vars = buildCommonVariables(invoice);

            // Notify the resolved Assistant(s) Comptable (N5)
            List<User> accounting = resolveAccountingRecipients(invoice);
            if (!accounting.isEmpty()) {
                notifyUsers(accounting, "Bon À Payer accordé / BAP issued", "invoice-approved", vars);
            }

            // Notify supplier: invoice approved for payment (N6)
            notifySupplier(invoice, "Votre facture a été approuvée pour paiement / Your invoice was approved for payment",
                    "supplier-invoice-approved", vars);
        });
    }
```

- [ ] **Step 6 : Créer le template `supplier-invoice-approved.html`**

Créer `src/main/resources/templates/email/supplier-invoice-approved.html` :

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Facture approuvée / Invoice Approved</title>
    <style>
        body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0; }
        .container { max-width: 600px; margin: 32px auto; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,.1); }
        .header { background: #1a3a5c; padding: 24px; text-align: center; }
        .header h1 { color: #fff; font-size: 20px; margin: 0; }
        .body { padding: 32px; color: #333; }
        .body p { line-height: 1.6; }
        .invoice-box { background: #f0fdf4; border-left: 4px solid #16a34a; padding: 16px 20px; border-radius: 4px; margin: 20px 0; }
        .invoice-box p { margin: 6px 0; }
        .label { font-weight: bold; color: #1a3a5c; }
        .badge { display: inline-block; background: #dcfce7; color: #16a34a; padding: 4px 12px; border-radius: 20px; font-weight: bold; font-size: 14px; margin-bottom: 12px; }
        .btn { display: inline-block; margin-top: 24px; padding: 12px 28px; background: #1a3a5c; color: #fff; text-decoration: none; border-radius: 6px; font-weight: bold; }
        .divider { border: none; border-top: 1px solid #e0e0e0; margin: 24px 0; }
        .footer { background: #f9f9f9; padding: 16px 32px; font-size: 12px; color: #888; text-align: center; }
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>OCT — Portail Fournisseurs</h1>
    </div>
    <div class="body">
        <div class="badge">✓ Approuvée / Approved</div>

        <!-- FR -->
        <p>Bonjour,</p>
        <p>Votre facture a été approuvée pour paiement par OCT (Bon à Payer accordé).</p>
        <div class="invoice-box">
            <p><span class="label">Référence OCT :</span> <span th:text="${reference}">FAC-2026-00001</span></p>
            <p><span class="label">Montant :</span> <span th:text="${amount}">0 XAF</span></p>
        </div>
        <p>Le paiement sera traité selon les délais convenus. Vous serez notifié dès son exécution.</p>

        <hr class="divider"/>

        <!-- EN -->
        <p>Hello,</p>
        <p>Your invoice has been approved for payment by OCT (Bon à Payer issued).</p>
        <div class="invoice-box">
            <p><span class="label">OCT Reference:</span> <span th:text="${reference}">FAC-2026-00001</span></p>
            <p><span class="label">Amount:</span> <span th:text="${amount}">0 XAF</span></p>
        </div>
        <p>Payment will be processed within the agreed terms. You will be notified once it is executed.</p>
        <a th:href="${frontendUrl + '/supplier/invoices'}" class="btn">View Invoice →</a>
    </div>
    <div class="footer">
        OCT — Owendo Container Terminal, Libreville, Gabon · Ne pas répondre à cet email / Do not reply to this email.
    </div>
</div>
</body>
</html>
```

- [ ] **Step 7 : Lancer les tests → vérifier qu'ils passent (vert)**

Run : `./mvnw test -Dtest=EmailNotificationListenerTest -q`
Attendu : PASS (8 tests).

- [ ] **Step 8 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/notification/event/listener/EmailNotificationListener.java \
        src/main/resources/templates/email/supplier-invoice-approved.html \
        src/test/java/com/oct/invoicesystem/domain/notification/event/listener/EmailNotificationListenerTest.java
git commit -m "fix(notif): N5/N6 (PROB-119/120) email rejet/BAP vers AA resolus + fournisseur notifie au BAP"
```

---

## Task 2 : Routage in-app (N5) sur rejet et BAP + in-app fournisseur (N5/N6) dans `PersistNotificationListener`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/notification/event/listener/PersistNotificationListener.java` (handlers `onInvoiceRejected` lignes 77-92, `onBonAPayer` lignes 97-112 ; ajout helper)
- Test: `src/test/java/com/oct/invoicesystem/domain/notification/event/listener/PersistNotificationListenerTest.java`

**Interfaces:**
- Consumes : `userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")`, `userRepository.findActiveUsersBySupplierId(UUID)`, `User.getAuthorities()`, helper privé existant `save(User, Invoice, String titleFr, String titleEn, String messageFr, String messageEn, NotificationType)`.
- Produces : helper privé `List<User> resolveAccountingRecipients(Invoice invoice)` (même logique que Task 1).

- [ ] **Step 1 : Réécrire le test → cas internes + N5/N6 (rouge)**

Remplacer intégralement `PersistNotificationListenerTest.java` par :

```java
package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.notification.model.Notification;
import com.oct.invoicesystem.domain.notification.model.NotificationType;
import com.oct.invoicesystem.domain.notification.repository.NotificationRepository;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistNotificationListenerTest {

    @Mock NotificationRepository notificationRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock UserRepository userRepository;

    @InjectMocks PersistNotificationListener listener;

    private Invoice invoice;
    private User aaSubmitter;
    private User n1User;
    private Supplier supplier;
    private User supplierUser;

    private static User withRole(User u, String roleName) {
        u.setUserRoles(Set.of(UserRole.builder()
                .role(Role.builder().name(roleName).build())
                .user(u)
                .build()));
        return u;
    }

    @BeforeEach
    void setUp() {
        Department dept = new Department();
        dept.setCode("DRH");
        dept.setN1Role("ROLE_VALIDATEUR_N1_DRH");
        dept.setRequiresN2(false);

        aaSubmitter = withRole(User.builder()
                .id(UUID.randomUUID()).username("assistant").email("a@oct.ga")
                .password("x").firstName("A").lastName("B").build(), "ROLE_ASSISTANT_COMPTABLE");

        n1User = User.builder()
                .id(UUID.randomUUID()).username("n1").email("n1@oct.ga")
                .password("x").firstName("N").lastName("One").build();

        supplier = Supplier.builder().id(UUID.randomUUID()).build();
        supplierUser = User.builder()
                .id(UUID.randomUUID()).username("supplier").email("supplier@acme.ga")
                .password("x").firstName("S").lastName("Up").supplier(supplier).build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID()).referenceNumber("FAC-2026-00001")
                .supplierName("Acme").amount(BigDecimal.valueOf(50_000)).currency("XAF")
                .supplier(supplier)
                .department(dept).submittedBy(aaSubmitter).build();
    }

    @Test
    void onInvoiceSubmitted_persistsNotificationForN1Approvers() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_VALIDATEUR_N1_DRH")).thenReturn(List.of(n1User));

        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, invoice.getId()));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.SUBMISSION);
        assertThat(captor.getValue().getUser()).isEqualTo(n1User);
    }

    // ── N5 : rejet, facture INTERNE (submitter = AA) → AA seul + fournisseur in-app ──
    @Test
    void onInvoiceRejected_internalInvoice_persistsForSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Wrong amount"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertThat(saved).extracting(Notification::getUser).contains(aaSubmitter, supplierUser);
        assertThat(saved).allMatch(n -> n.getType() == NotificationType.REJECTION);
        assertThat(saved.stream().anyMatch(n -> n.getMessageFr().contains("Wrong amount"))).isTrue();
        verify(userRepository, never()).findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");
    }

    // ── N5 : rejet, facture PORTAIL (submitter = fournisseur) → AA actifs + fournisseur in-app ──
    @Test
    void onInvoiceRejected_portalInvoice_persistsForActiveAAsAndSupplier() {
        invoice.setSubmittedBy(supplierUser); // fournisseur, non-AA
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Wrong amount"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getUser).contains(aaSubmitter, supplierUser);
    }

    // ── N6 : BAP, facture INTERNE → AA seul + fournisseur in-app ──
    @Test
    void onBonAPayer_internalInvoice_persistsForSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertThat(saved).extracting(Notification::getUser).contains(aaSubmitter, supplierUser);
        assertThat(saved).allMatch(n -> n.getType() == NotificationType.APPROVAL);
    }

    // ── N5+N6 : BAP, facture PORTAIL → AA actifs + fournisseur in-app ──
    @Test
    void onBonAPayer_portalInvoice_persistsForActiveAAsAndSupplier() {
        invoice.setSubmittedBy(supplierUser);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getUser).contains(aaSubmitter, supplierUser);
    }

    @Test
    void onInvoiceSubmitted_doesNothing_whenInvoiceNotFound() {
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());
        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, UUID.randomUUID()));
        verifyNoInteractions(notificationRepository);
    }
}
```

- [ ] **Step 2 : Lancer les tests → rouge**

Run : `./mvnw test -Dtest=PersistNotificationListenerTest -q`
Attendu : ÉCHEC. Rejet et BAP ne persistent qu'une notif (submitter), pas la notif fournisseur ; le cas portail ne résout pas les AA.

- [ ] **Step 3 : Ajouter le helper `resolveAccountingRecipients`**

Dans `PersistNotificationListener.java`, juste avant `private void save(...)` (ligne 184), insérer :

```java
    /**
     * Résout le(s) destinataire(s) interne(s) « Assistant Comptable » d'une facture.
     * Si le soumetteur porte {@code ROLE_ASSISTANT_COMPTABLE} (facture interne), il est seul
     * destinataire ; sinon (facture portail : soumetteur = compte fournisseur), on cible tous les
     * AA actifs.
     */
    private List<User> resolveAccountingRecipients(Invoice invoice) {
        User submitter = invoice.getSubmittedBy();
        if (submitter != null && submitter.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ASSISTANT_COMPTABLE".equals(a.getAuthority()))) {
            return List.of(submitter);
        }
        return userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");
    }
```

- [ ] **Step 4 : Réécrire `onInvoiceRejected` (lignes 77-92)**

Remplacer le corps par :

```java
    @Async
    @EventListener
    public void onInvoiceRejected(InvoiceRejectedEvent event) {
        log.info("Persisting notification for InvoiceRejectedEvent {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            // Assistant(s) Comptable résolus (N5)
            resolveAccountingRecipients(invoice).forEach(user -> save(user, invoice,
                    "Facture rejetée",
                    "Invoice rejected",
                    "La facture " + invoice.getReferenceNumber() + " a été rejetée. Motif : " + event.getReason(),
                    "Invoice " + invoice.getReferenceNumber() + " was rejected. Reason: " + event.getReason(),
                    NotificationType.REJECTION));

            // Fournisseur (in-app) (N5)
            if (invoice.getSupplier() != null) {
                userRepository.findActiveUsersBySupplierId(invoice.getSupplier().getId()).forEach(user ->
                        save(user, invoice,
                                "Facture rejetée",
                                "Invoice rejected",
                                "Votre facture " + invoice.getReferenceNumber() + " a été rejetée. Motif : " + event.getReason(),
                                "Your invoice " + invoice.getReferenceNumber() + " was rejected. Reason: " + event.getReason(),
                                NotificationType.REJECTION));
            }
        });
    }
```

- [ ] **Step 5 : Réécrire `onBonAPayer` (lignes 97-112)**

Remplacer le corps par :

```java
    @Async
    @EventListener
    public void onBonAPayer(BonAPayerEvent event) {
        log.info("Persisting notification for BonAPayerEvent {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            // Assistant(s) Comptable résolus (N5)
            resolveAccountingRecipients(invoice).forEach(user -> save(user, invoice,
                    "Bon à Payer accordé",
                    "BAP issued",
                    "La facture " + invoice.getReferenceNumber() + " a reçu le Bon à Payer.",
                    "Invoice " + invoice.getReferenceNumber() + " has received the BAP approval.",
                    NotificationType.APPROVAL));

            // Fournisseur (in-app) (N6)
            if (invoice.getSupplier() != null) {
                userRepository.findActiveUsersBySupplierId(invoice.getSupplier().getId()).forEach(user ->
                        save(user, invoice,
                                "Facture approuvée pour paiement",
                                "Invoice approved for payment",
                                "Votre facture " + invoice.getReferenceNumber() + " a été approuvée pour paiement.",
                                "Your invoice " + invoice.getReferenceNumber() + " has been approved for payment.",
                                NotificationType.APPROVAL));
            }
        });
    }
```

- [ ] **Step 6 : Lancer les tests → vert**

Run : `./mvnw test -Dtest=PersistNotificationListenerTest -q`
Attendu : PASS (7 tests).

- [ ] **Step 7 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/notification/event/listener/PersistNotificationListener.java \
        src/test/java/com/oct/invoicesystem/domain/notification/event/listener/PersistNotificationListenerTest.java
git commit -m "fix(notif): N5/N6 (PROB-119/120) in-app rejet/BAP vers AA resolus + fournisseur notifie"
```

---

## Task 3 : Gate complet + living-doc + vérification runtime MailHog

**Files:**
- Modify: `docs/KNOWN_ISSUES_REGISTRY.md` (via heredoc — octet NUL), `docs/TASKS.md`

- [ ] **Step 1 : Gate backend complet depuis un surefire-reports vide**

```bash
rm -rf target/surefire-reports
export DB_NAME=oct_invoice DB_USER=postgres DB_PASSWORD=dany
./mvnw test
```
Attendu : `Tests run: <>=614+nouveaux>, Failures: 0, Errors: 0, Skipped: 0`. Si un test d'un AUTRE fichier casse (ex. flaky state-machine), STOP et signaler — ne pas maquiller.

- [ ] **Step 2 : Vérification runtime MailHog (facture portail)**

Déployer le backend, puis via le workflow : compte `supplier` soumet une facture → `aa`/contrôle AA → validateur → `daf`. Sur **rejet** puis sur **BAP**, vérifier dans MailHog (`http://localhost:8025`) qu'il y a à chaque étape **un mail aux AA + un mail fournisseur** (sur BAP, le fournisseur reçoit le nouveau template « approuvée pour paiement »), et dans l'app une notif in-app pour l'AA et pour le fournisseur.
Déploiement : `./mvnw.cmd -q -DskipTests package` → `docker cp target/invoice-system-1.0.0-SNAPSHOT.jar oct_backend:/app/app.jar` → `docker restart oct_backend`.

- [ ] **Step 3 : Living-doc — KNOWN_ISSUES_REGISTRY.md (heredoc, jamais Edit)**

```bash
cat >> docs/KNOWN_ISSUES_REGISTRY.md <<'EOF'

## PROB-119 — Notifications rejet/BAP ciblaient le mauvais destinataire interne (N5)
**Date:** 2026-07-18
**Root cause:** `EmailNotificationListener.onInvoiceRejected/onBonAPayer` et leurs équivalents
`PersistNotificationListener` ciblaient `invoice.getSubmittedBy()` en supposant que c'est l'Assistant
Comptable. Pour une facture soumise via le portail fournisseur, `submittedBy` = le compte fournisseur :
aucun Assistant Comptable n'était prévenu de reprendre le dossier, et le fournisseur recevait le mail
interne (mauvais template).
**Solution:** Helper `resolveAccountingRecipients(Invoice)` dans les deux listeners : renvoie
`[submittedBy]` si le soumetteur porte `ROLE_ASSISTANT_COMPTABLE`, sinon
`findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")`. Rejet et BAP ciblent désormais ce résultat.
**Preventive rule:** Ne jamais présumer le rôle de `submittedBy` : une facture portail est soumise par
le fournisseur. Résoudre le destinataire métier par son rôle, pas par le champ soumetteur.

## PROB-120 — Le fournisseur n'était pas notifié au Bon à Payer (N6)
**Date:** 2026-07-18
**Root cause:** `onBonAPayer` (email ET in-app) n'appelait aucun `notifySupplier` /
`findActiveUsersBySupplierId`, contrairement à `onInvoiceRejected`/`onInvoicePayed`. Le fournisseur
n'était jamais informé de l'approbation de sa facture — violation de la matrice §7 (« Invoice approved
(BON_A_PAYER) → Supplier »).
**Solution:** Ajout du versant fournisseur sur BAP : email nouveau template
`supplier-invoice-approved.html` + notification in-app (`findActiveUsersBySupplierId`,
`NotificationType.APPROVAL`).
**Preventive rule:** Tout événement de cycle de vie visible côté fournisseur (soumission, rejet,
approbation, paiement) doit déclencher une notification fournisseur ; vérifier la matrice §7 à chaque
nouvel événement.
EOF
```

- [ ] **Step 4 : Living-doc — docs/TASKS.md (marquer N5/N6 corrigés)**

Repérer les entrées N5 et N6 dans `docs/TASKS.md` (Grep `N5`, `N6`) et les marquer corrigés (2026-07-18, PROB-119/120, branche `fix/notif-n5-n6`), dans le style des autres lignes déjà cochées.

- [ ] **Step 5 : Commit living-doc**

```bash
git add docs/KNOWN_ISSUES_REGISTRY.md docs/TASKS.md
git commit -m "docs(notif): PROB-119 (N5) + PROB-120 (N6) — registre et TASKS"
```

---

## Self-Review (effectuée)

- **Couverture spec :** §2 règle de résolution → helper (T1 s3, T2 s3) ; §2 matrice rejet/BAP interne+fournisseur → T1 s4-s5 (email) + T2 s4-s5 (in-app) ; §3 template BAP → T1 s6 ; §4 les 5 cas de test → couverts (rejet portail, rejet interne, BAP portail, BAP interne, + résolution AA implicite dans chaque cas) ; §5 runtime MailHog → T3 s2 ; §6 i18n en dur → aucune clé ajoutée ; §7 living-doc → T3 s3-s4.
- **Placeholders :** aucun — tout le code des étapes est complet.
- **Cohérence des types :** `resolveAccountingRecipients(Invoice) : List<User>` identique dans les deux listeners ; `save(...)` 7-args réutilisé tel quel ; `notifyUsers`/`notifySupplier`/`buildCommonVariables` inchangés ; `findActiveUsersBySupplierId(UUID)` et `findActiveUsersByRoleName(String)` conformes au repo.
- **Piège traité :** les 2 tests existants supposant `submittedBy` sans rôle sont réécrits en « facture interne » (submitter = AA) dans la même tâche que le code, évitant une régression de gate.
