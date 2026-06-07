package com.oct.invoicesystem.domain.user.repository;

import com.oct.invoicesystem.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.userRoles ur WHERE ur.role.name = :roleName AND u.active = true")
    java.util.List<User> findActiveUsersByRoleName(String roleName);

    @Query("SELECT u FROM User u WHERE u.supplier.id = :supplierId AND u.active = true")
    java.util.List<User> findActiveUsersBySupplierId(java.util.UUID supplierId);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByPasswordResetToken(String token);
}
