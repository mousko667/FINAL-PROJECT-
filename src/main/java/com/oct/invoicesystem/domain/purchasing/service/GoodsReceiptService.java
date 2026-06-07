package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.purchasing.dto.GoodsReceiptCreateRequest;
import com.oct.invoicesystem.domain.purchasing.dto.GoodsReceiptDTO;
import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptItem;
import com.oct.invoicesystem.domain.purchasing.model.GoodsReceiptNote;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderItem;
import com.oct.invoicesystem.domain.purchasing.repository.GoodsReceiptItemRepository;
import com.oct.invoicesystem.domain.purchasing.repository.GoodsReceiptNoteRepository;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderItemRepository;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GoodsReceiptService {

    private final GoodsReceiptNoteRepository grnRepository;
    private final GoodsReceiptItemRepository grnItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final UserRepository userRepository;

    public GoodsReceiptDTO createGRN(GoodsReceiptCreateRequest request) {
        if (grnRepository.findByGrnNumber(request.grnNumber()).isPresent()) {
            throw new ValidationException("GRN number already exists: " + request.grnNumber());
        }

        PurchaseOrder po = purchaseOrderRepository.findByIdActive(request.purchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + request.purchaseOrderId()));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User receiver = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + auth.getName()));

        GoodsReceiptNote grn = GoodsReceiptNote.builder()
                .grnNumber(request.grnNumber())
                .purchaseOrder(po)
                .receivedBy(receiver)
                .receiptDate(request.receiptDate())
                .build();
        GoodsReceiptNote saved = grnRepository.save(grn);

        if (request.items() != null) {
            for (var itemReq : request.items()) {
                PurchaseOrderItem poItem = purchaseOrderItemRepository.findById(itemReq.purchaseOrderItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("PO item not found: " + itemReq.purchaseOrderItemId()));
                GoodsReceiptItem item = GoodsReceiptItem.builder()
                        .goodsReceiptNote(saved)
                        .purchaseOrderItem(poItem)
                        .receivedQuantity(itemReq.receivedQuantity())
                        .build();
                grnItemRepository.save(item);
            }
        }

        log.info("GRN {} created for PO {}", request.grnNumber(), po.getPoNumber());
        return toDTO(grnRepository.findById(saved.getId()).orElse(saved));
    }

    @Transactional(readOnly = true)
    public GoodsReceiptDTO getGRN(UUID id) {
        return toDTO(grnRepository.findById(id)
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("GRN not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptDTO> getGRNsByPurchaseOrder(UUID purchaseOrderId) {
        return grnRepository.findByPurchaseOrderId(purchaseOrderId).stream()
                .map(this::toDTO)
                .toList();
    }

    private GoodsReceiptDTO toDTO(GoodsReceiptNote grn) {
        List<GoodsReceiptDTO.GoodsReceiptItemDTO> itemDTOs = grn.getItems().stream().map(item ->
                new GoodsReceiptDTO.GoodsReceiptItemDTO(
                        item.getId(),
                        item.getPurchaseOrderItem().getId(),
                        item.getPurchaseOrderItem().getItemDescription(),
                        item.getReceivedQuantity()
                )
        ).toList();

        return new GoodsReceiptDTO(
                grn.getId(),
                grn.getGrnNumber(),
                grn.getPurchaseOrder().getId(),
                grn.getPurchaseOrder().getPoNumber(),
                grn.getReceivedBy().getUsername(),
                grn.getReceiptDate(),
                itemDTOs,
                grn.getCreatedAt()
        );
    }
}
