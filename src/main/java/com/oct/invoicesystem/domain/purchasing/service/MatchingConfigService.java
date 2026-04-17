package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import com.oct.invoicesystem.domain.purchasing.repository.MatchingConfigRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for managing MatchingConfig.
 * Handles creating and updating the matching tolerance configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MatchingConfigService {

    private final MatchingConfigRepository matchingConfigRepository;

    /**
     * Get the active matching configuration.
     *
     * @return the active MatchingConfig
     * @throws ResourceNotFoundException if no active config found
     */
    @Transactional(readOnly = true)
    public MatchingConfig getActiveConfig() {
        return matchingConfigRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No active matching configuration found"));
    }

    /**
     * Get matching configuration by ID.
     *
     * @param id the config ID
     * @return the MatchingConfig
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public MatchingConfig getConfig(UUID id) {
        return matchingConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Matching configuration not found: " + id));
    }

    /**
     * Update the matching configuration with new tolerance and GRN requirements.
     * Creates a new config entry while deactivating the old one.
     *
     * @param tolerancePercentage the new tolerance percentage
     * @param toleranceAmount the new tolerance amount
     * @param requireGrn the new GRN requirement flag
     * @param updatedBy the user making the update
     * @return the new MatchingConfig
     * @throws ValidationException if values are invalid
     */
    public MatchingConfig updateConfig(BigDecimal tolerancePercentage, 
                                       BigDecimal toleranceAmount, 
                                       Boolean requireGrn, 
                                       User updatedBy) {
        // Validate inputs
        if (tolerancePercentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Tolerance percentage cannot be negative");
        }
        if (tolerancePercentage.compareTo(new BigDecimal("100")) > 0) {
            throw new ValidationException("Tolerance percentage cannot exceed 100");
        }
        if (toleranceAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Tolerance amount cannot be negative");
        }

        // Deactivate existing active config
        matchingConfigRepository.findByIsActiveTrue()
                .ifPresent(config -> {
                    config.setIsActive(false);
                    matchingConfigRepository.save(config);
                    log.info("Deactivated previous matching configuration {}", config.getId());
                });

        // Create new config
        MatchingConfig newConfig = MatchingConfig.builder()
                .tolerancePercentage(tolerancePercentage)
                .toleranceAmount(toleranceAmount)
                .requireGrn(requireGrn)
                .isActive(true)
                .updatedBy(updatedBy)
                .build();

        log.info("Creating new matching configuration with tolerance {}%, amount {}", 
                tolerancePercentage, toleranceAmount);
        return matchingConfigRepository.save(newConfig);
    }
}
