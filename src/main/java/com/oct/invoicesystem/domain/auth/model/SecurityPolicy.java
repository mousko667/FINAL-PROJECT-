package com.oct.invoicesystem.domain.auth.model;

import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * System-wide security policy (P11-40 / REQ-02). Singleton row (one {@code is_active=true}),
 * versioned on update. Replaces the former simulation-only SecuritySettingsPage form.
 */
@Entity
@Table(name = "security_policy")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mfa_required", nullable = false)
    @Builder.Default
    private Boolean mfaRequired = true;

    @Column(name = "session_timeout_minutes", nullable = false)
    @Builder.Default
    private Integer sessionTimeoutMinutes = 60;

    @Column(name = "max_login_attempts", nullable = false)
    @Builder.Default
    private Integer maxLoginAttempts = 5;

    @Column(name = "min_password_length", nullable = false)
    @Builder.Default
    private Integer minPasswordLength = 8;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Nullable: a system-seeded default policy (created at startup if none exists) has no
    // human author. A policy saved via the admin UI always has updatedBy set.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
