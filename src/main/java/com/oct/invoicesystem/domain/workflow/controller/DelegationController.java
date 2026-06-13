package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.dto.DelegationDTO;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.service.DelegationService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals/delegations")
@RequiredArgsConstructor
@Tag(name = "Approval Delegation", description = "Délégation d'approbation pour absences")
public class DelegationController {

    private final DelegationService delegationService;
    private final SecurityHelper securityHelper;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une délégation d'approbation")
    public ResponseEntity<ApiResponse<DelegationDTO>> createDelegation(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        User admin = securityHelper.currentUser(authentication);
        ApprovalDelegation d = delegationService.createDelegation(
                UUID.fromString((String) request.get("delegatorId")),
                UUID.fromString((String) request.get("delegateeId")),
                (String) request.get("departmentCode"),
                LocalDate.parse((String) request.get("fromDate")),
                LocalDate.parse((String) request.get("toDate")),
                (String) request.get("reason"),
                admin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toDTO(d)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister les délégations actives par département")
    public ResponseEntity<ApiResponse<List<DelegationDTO>>> listDelegations(
            @RequestParam(required = false) String departmentCode) {
        List<ApprovalDelegation> delegations = departmentCode != null
                ? delegationService.getActiveDelegationsForDepartment(departmentCode)
                : List.of();
        List<DelegationDTO> result = delegations.stream().map(this::toDTO).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private DelegationDTO toDTO(ApprovalDelegation d) {
        return new DelegationDTO(
                d.getId(),
                d.getDelegator().getUsername(),
                d.getDelegatee().getUsername(),
                d.getDepartmentCode(),
                d.getFromDate(),
                d.getToDate(),
                d.getReason() != null ? d.getReason() : "",
                d.getCreatedAt());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Révoquer une délégation")
    public ResponseEntity<ApiResponse<Void>> revokeDelegation(@PathVariable UUID id) {
        delegationService.revokeDelegation(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Délégation révoquée"));
    }
}
