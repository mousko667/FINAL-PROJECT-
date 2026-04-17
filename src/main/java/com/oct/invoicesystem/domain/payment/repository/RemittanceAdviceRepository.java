package com.oct.invoicesystem.domain.payment.repository;

import com.oct.invoicesystem.domain.payment.model.RemittanceAdvice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RemittanceAdviceRepository extends JpaRepository<RemittanceAdvice, UUID> {
    Optional<RemittanceAdvice> findByPaymentId(UUID paymentId);
}
