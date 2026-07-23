package com.oct.invoicesystem.domain.purchasing.controller;

import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptItem;
import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptNote;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderItem;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderStatus;
import com.oct.invoicesystem.domain.purchasing.repository.GoodsReceiptNoteRepository;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUDIT-028 — {@code GET /api/v1/goods-receipts} renvoyait une liste vide **codee en dur** quand le
 * parametre facultatif {@code purchaseOrderId} etait absent, alors que la page « Bons de Reception »
 * appelle l'endpoint sans aucun parametre : la liste etait structurellement toujours vide, bien que
 * la base contienne des GRN (11 en runtime, aucun supprime).
 *
 * <p>Ce controleur faisait partie des 14 non testes recenses par AUDIT-013 — le defaut serait tombe
 * avec un test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("GoodsReceiptController — liste des bons de reception (AUDIT-028)")
class GoodsReceiptControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GoodsReceiptNoteRepository grnRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private PurchaseOrder purchaseOrder;
    private PurchaseOrder otherPurchaseOrder;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        User receiver = userRepository.save(User.builder()
                .username("grn_receiver_" + unique)
                .email("grn_receiver_" + unique + "@test.local")
                .password(passwordEncoder.encode("Password!1"))
                .firstName("Grn").lastName("Receiver").preferredLang("fr")
                .build());

        Supplier supplier = supplierRepository.save(Supplier.builder()
                .companyName("GRN Supplier " + unique)
                .contactEmail("grn_supplier_" + unique + "@test.com")
                .taxId("TAX-" + unique)
                .build());

        purchaseOrder = savePurchaseOrder("PO-GRN-A-" + unique, supplier, receiver);
        otherPurchaseOrder = savePurchaseOrder("PO-GRN-B-" + unique, supplier, receiver);

        // 2 GRN sur le premier BC, 1 sur le second : la liste sans filtre doit en voir 3, et le
        // filtre par BC doit en isoler 2.
        saveGrn("GRN-A1-" + unique, purchaseOrder, receiver, null);
        saveGrn("GRN-A2-" + unique, purchaseOrder, receiver, null);
        saveGrn("GRN-B1-" + unique, otherPurchaseOrder, receiver, null);
        // Un GRN supprime, qui ne doit apparaitre dans aucune des deux branches.
        saveGrn("GRN-DEL-" + unique, purchaseOrder, receiver, Instant.now());
    }

    /** Le cas du finding : la page appelle l'endpoint sans aucun parametre. */
    @Test
    @WithMockUser(username = "aa_grn", roles = {"ASSISTANT_COMPTABLE"})
    @DisplayName("liste sans filtre → tous les GRN non supprimes")
    void listGRNs_withoutFilter_returnsAllActiveGrns() throws Exception {
        mockMvc.perform(get("/api/v1/goods-receipts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    /** Contre-preuve : la branche filtree, seule a fonctionner avant le correctif, marche toujours. */
    @Test
    @WithMockUser(username = "aa_grn", roles = {"ASSISTANT_COMPTABLE"})
    @DisplayName("liste filtree par bon de commande → seulement les GRN de ce BC")
    void listGRNs_filteredByPurchaseOrder_returnsOnlyItsGrns() throws Exception {
        mockMvc.perform(get("/api/v1/goods-receipts")
                        .param("purchaseOrderId", purchaseOrder.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    /** Le DAF consulte les receptions ; la fermeture de l'endpoint ne doit pas l'exclure. */
    @Test
    @WithMockUser(username = "daf_grn", roles = {"DAF"})
    @DisplayName("le DAF accede aussi a la liste")
    void listGRNs_asDaf_returnsAllActiveGrns() throws Exception {
        mockMvc.perform(get("/api/v1/goods-receipts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3));
    }

    /**
     * SoD : ROLE_ADMIN n'a pas d'acces financier, et les receptions de marchandises alimentent le
     * rapprochement tripartite. La liste doit lui etre fermee.
     */
    @Test
    @WithMockUser(username = "admin_grn", roles = {"ADMIN"})
    @DisplayName("ADMIN → 403 (separation des devoirs)")
    void listGRNs_asAdmin_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/goods-receipts"))
                .andExpect(status().isForbidden());
    }

    private PurchaseOrder savePurchaseOrder(String poNumber, Supplier supplier, User createdBy) {
        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber(poNumber)
                .supplier(supplier)
                .totalAmount(new BigDecimal("1000.00"))
                .status(PurchaseOrderStatus.OPEN)
                .createdBy(createdBy)   // colonne NOT NULL en base
                .build();
        po = purchaseOrderRepository.save(po);

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .itemDescription("Widget")
                .quantity(new BigDecimal("10"))
                .unitPrice(new BigDecimal("100.00"))
                .lineTotal(new BigDecimal("1000.00"))   // colonne NOT NULL en base
                .build();
        po.getItems().add(item);
        return purchaseOrderRepository.save(po);
    }

    private void saveGrn(String grnNumber, PurchaseOrder po, User receiver, Instant deletedAt) {
        GoodsReceiptNote grn = GoodsReceiptNote.builder()
                .grnNumber(grnNumber)
                .purchaseOrder(po)
                .receivedBy(receiver)
                .receiptDate(LocalDate.now())
                .deletedAt(deletedAt)
                .build();
        grn = grnRepository.save(grn);

        GoodsReceiptItem item = GoodsReceiptItem.builder()
                .goodsReceiptNote(grn)
                .purchaseOrderItem(po.getItems().get(0))
                .receivedQuantity(new BigDecimal("10"))
                .build();
        grn.getItems().add(item);
        grnRepository.save(grn);
    }
}
