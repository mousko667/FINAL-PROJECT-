package com.oct.invoicesystem.domain.purchasing.controller;

import com.oct.invoicesystem.domain.purchasing.dto.MatchingDetailDTO;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingSummaryDTO;
import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import com.oct.invoicesystem.domain.purchasing.service.MatchingQueryService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Page de rapprochement dédiée (M5 #1/#4) — lecture seule. Staff hors SUPPLIER/ADMIN (SoD). */
@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
@Tag(name = "Matching", description = "Liste et comparaison ligne-à-ligne des rapprochements 3-voies")
public class MatchingQueryController {

    private final MatchingQueryService service;

    @GetMapping
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Liste des rapprochements", description = "Dernier résultat de rapprochement par facture, filtrable par statut/recherche.")
    public ResponseEntity<ApiResponse<PagedResponse<MatchingSummaryDTO>>> list(
            @RequestParam(required = false) MatchingStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<MatchingSummaryDTO> result = service.list(status, search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(result)));
    }

    @GetMapping("/{invoiceId}/lines")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Comparaison ligne-à-ligne", description = "Recompose la comparaison PO/GRN/facture par ligne pour une facture.")
    public ResponseEntity<ApiResponse<MatchingDetailDTO>> lines(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.success(service.getLines(invoiceId)));
    }
}
