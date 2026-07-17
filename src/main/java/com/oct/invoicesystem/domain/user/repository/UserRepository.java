package com.oct.invoicesystem.domain.user.repository;

import com.oct.invoicesystem.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<User> findByUsername(String username);

    // PROB-080: load roles eagerly so the entity can be mapped to a DTO outside the transaction
    // (e.g. UserProfileController.updateProfile) without a LazyInitializationException.
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<User> findWithRolesById(UUID id);
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.userRoles ur WHERE ur.role.name = :roleName AND u.active = true")
    java.util.List<User> findActiveUsersByRoleName(String roleName);

    @Query("SELECT u FROM User u WHERE u.supplier.id = :supplierId AND u.active = true")
    java.util.List<User> findActiveUsersBySupplierId(java.util.UUID supplierId);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByPasswordResetToken(String token);

    // P11-53: security-health metrics.
    long countByActiveTrue();

    long countByActiveTrueAndMfaEnabledTrue();

    @Query("SELECT COUNT(u) FROM User u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil > :now")
    long countLockedAccounts(java.time.Instant now);

    @Query("SELECT COALESCE(SUM(u.failedLoginAttempts), 0) FROM User u")
    long sumFailedLoginAttempts();

    // M13 #3: charge en une passe les users d'un ensemble de départements + leurs rôles (évite N+1).
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    java.util.List<User> findByDepartmentIdIn(Collection<UUID> departmentIds);

    // PROB-089: id-only projection so callers that only need the FK (e.g. tests referencing a
    // user via getReferenceById) never materialize — and thus never decrypt — encrypted columns
    // such as mfa_secret.
    @Query("select u.id from User u where u.username = :username")
    Optional<UUID> findIdByUsername(String username);
}
