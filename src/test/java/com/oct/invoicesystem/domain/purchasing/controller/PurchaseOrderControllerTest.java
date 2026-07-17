package com.oct.invoicesystem.domain.purchasing.controller;

import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderDTO;
import com.oct.invoicesystem.domain.purchasing.mapper.PurchaseOrderMapper;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderStatus;
import com.oct.invoicesystem.domain.purchasing.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PurchaseOrderService purchaseOrderService;

    @MockBean
    private PurchaseOrderMapper purchaseOrderMapper;

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void listPurchaseOrders_NoSupplierId_ReturnsPagedResponse() throws Exception {
        PurchaseOrder po1 = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-0001")
                .totalAmount(BigDecimal.TEN)
                .status(PurchaseOrderStatus.OPEN)
                .items(List.of())
                .build();
        PurchaseOrder po2 = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-0002")
                .totalAmount(BigDecimal.ONE)
                .status(PurchaseOrderStatus.OPEN)
                .items(List.of())
                .build();

        Page<PurchaseOrder> page = new PageImpl<>(List.of(po1, po2), PageRequest.of(0, 20), 2);
        when(purchaseOrderService.listAll(any())).thenReturn(page);

        PurchaseOrderDTO dto1 = new PurchaseOrderDTO(po1.getId(), "PO-0001", null, BigDecimal.TEN, "OPEN", null, List.of(), null, null);
        PurchaseOrderDTO dto2 = new PurchaseOrderDTO(po2.getId(), "PO-0002", null, BigDecimal.ONE, "OPEN", null, List.of(), null, null);
        when(purchaseOrderMapper.toPurchaseOrderDTO(po1)).thenReturn(dto1);
        when(purchaseOrderMapper.toPurchaseOrderDTO(po2)).thenReturn(dto2);

        mockMvc.perform(get("/api/v1/purchase-orders").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].poNumber").value("PO-0001"))
                .andExpect(jsonPath("$.data.content[1].poNumber").value("PO-0002"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.last").value(true));

        verify(purchaseOrderService).listAll(any());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void listPurchaseOrders_WithSupplierId_ReturnsSinglePagePagedResponse() throws Exception {
        UUID supplierId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-0003")
                .totalAmount(BigDecimal.TEN)
                .status(PurchaseOrderStatus.OPEN)
                .items(List.of())
                .build();

        when(purchaseOrderService.listBySupplier(supplierId)).thenReturn(List.of(po));

        PurchaseOrderDTO dto = new PurchaseOrderDTO(po.getId(), "PO-0003", supplierId, BigDecimal.TEN, "OPEN", null, List.of(), null, null);
        when(purchaseOrderMapper.toPurchaseOrderDTO(po)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/purchase-orders").param("supplierId", supplierId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].poNumber").value("PO-0003"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listPurchaseOrders_AsUserWithoutRole_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-orders"))
                .andExpect(status().isForbidden());
    }
}
