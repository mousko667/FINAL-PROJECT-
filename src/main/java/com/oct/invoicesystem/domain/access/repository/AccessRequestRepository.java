package com.oct.invoicesystem.domain.access.repository;

import com.oct.invoicesystem.domain.access.model.AccessRequest;
import com.oct.invoicesystem.domain.access.model.AccessRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

    Page<AccessRequest> findByStatus(AccessRequestStatus status, Pageable pageable);

    Page<AccessRequest> findByRequesterId(UUID requesterId, Pageable pageable);

    boolean existsByRequesterIdAndRequestedRoleAndStatus(
            UUID requesterId, String requestedRole, AccessRequestStatus status);
}
