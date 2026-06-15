package com.oct.invoicesystem.domain.compliance.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Records a user's acceptance of a privacy-policy version (M14). */
@Entity
@Table(name = "privacy_policy_acceptances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PrivacyPolicyAcceptance {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;

    @CreationTimestamp
    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;
}
