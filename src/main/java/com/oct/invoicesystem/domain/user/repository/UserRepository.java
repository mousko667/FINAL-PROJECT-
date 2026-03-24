package com.oct.invoicesystem.domain.user.repository;

import com.oct.invoicesystem.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
