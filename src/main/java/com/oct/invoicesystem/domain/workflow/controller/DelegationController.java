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

    // ── Self-service (M6): an approver manages THEIR OWN delegations ──────────────
    private static final String APPROVER_ROLES =
            "hasAnyRole('DAF') or hasAuthority('ROLE_VALIDATEUR_N1_DRH') or hasAuthority('ROLE_VALIDATEUR_N1_DG') "
          + "or hasAuthority('ROLE_VALIDATEUR_N1_INFO') or hasAuthority('ROLE_VALIDATEUR_N2_INFO') "
          + "or hasAuthority('ROLE_VALIDATEUR_N1_TERM') or hasAuthority('ROLE_VALIDATEUR_N1_COM') "
          + "or hasAuthority('ROLE_VALIDATEUR_N1_QHSSE') or hasAuthority('ROLE_VALIDATEUR_N1_INFRA') "
          + "or hasAuthority('ROLE_VALIDATEUR_N2_INFRA') or hasAuthority('ROLE_VALIDATEUR_N1_TECH') "
          + "or hasAuthority('ROLE_VALIDATEUR_N2_TECH')";

    @PostMapping("/mine")
    @PreAuthorize(APPROVER_ROLES)
    @Operation(summary = "Déléguer mes propres approbations (absence)")
    public ResponseEntity<ApiResponse<DelegationDTO>> createMyDelegation(
            @RequestBody Map<String, Object> request, Authentication authentication) {
        User me = securityHelper.currentUser(authentication);
        Object deptObj = request.get("departmentCode");
        ApprovalDelegation d = delegationService.createSelfDelegation(
                me,
                UUID.fromString((String) request.get("delegateeId")),
                deptObj == null ? null : (String) deptObj,
                LocalDate.parse((String) request.get("fromDate")),
                LocalDate.parse((String) request.get("toDate")),
                (String) request.get("reason"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toDTO(d)));
    }

    @GetMapping("/mine")
    @PreAuthorize(APPROVER_ROLES)
    @Operation(summary = "Lister mes délégations")
    public ResponseEntity<ApiResponse<List<DelegationDTO>>> listMyDelegations(Authentication authentication) {
        UUID myId = securityHelper.currentUserId(authentication);
        List<DelegationDTO> result = delegationService.getMyDelegations(myId).stream().map(this::toDTO).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/eligible-delegatees")
    @PreAuthorize(APPROVER_ROLES)
    @Operation(summary = "Lister les délégataires possibles (staff actif)")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> eligibleDelegatees(Authentication authentication) {
        UUID myId = securityHelper.currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(delegationService.getEligibleDelegatees(myId)));
    }

    @DeleteMapping("/mine/{id}")
    @PreAuthorize(APPROVER_ROLES)
    @Operation(summary = "Révoquer une de mes délégations")
    public ResponseEntity<ApiResponse<Void>> revokeMyDelegation(
            @PathVariable UUID id, Authentication authentication) {
        delegationService.revokeOwnDelegation(id, securityHelper.currentUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success(null, "Délégation révoquée"));
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
