package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.service.DelegationService;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
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
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une délégation d'approbation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDelegation(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        User admin = securityHelper.currentUser(authentication);
        User delegator = userRepository.findById(UUID.fromString((String) request.get("delegatorId")))
                .orElseThrow(() -> new ResourceNotFoundException("Delegator not found"));
        User delegatee = userRepository.findById(UUID.fromString((String) request.get("delegateeId")))
                .orElseThrow(() -> new ResourceNotFoundException("Delegatee not found"));
        ApprovalDelegation d = delegationService.createDelegation(
                delegator, delegatee,
                (String) request.get("departmentCode"),
                LocalDate.parse((String) request.get("fromDate")),
                LocalDate.parse((String) request.get("toDate")),
                (String) request.get("reason"),
                admin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("id", d.getId(), "createdAt", d.getCreatedAt())));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister les délégations actives par département")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listDelegations(
            @RequestParam(required = false) String departmentCode) {
        List<ApprovalDelegation> delegations = departmentCode != null
                ? delegationService.getActiveDelegationsForDepartment(departmentCode)
                : List.of();
        List<Map<String, Object>> result = delegations.stream().map(d -> Map.of(
                "id", (Object) d.getId(),
                "delegatorUsername", d.getDelegator().getUsername(),
                "delegateeUsername", d.getDelegatee().getUsername(),
                "departmentCode", d.getDepartmentCode(),
                "fromDate", d.getFromDate().toString(),
                "toDate", d.getToDate().toString(),
                "reason", d.getReason() != null ? d.getReason() : ""
        )).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Révoquer une délégation")
    public ResponseEntity<ApiResponse<Void>> revokeDelegation(@PathVariable UUID id) {
        delegationService.revokeDelegation(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Délégation révoquée"));
    }
}
