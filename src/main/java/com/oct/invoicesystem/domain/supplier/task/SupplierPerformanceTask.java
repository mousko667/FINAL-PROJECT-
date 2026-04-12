package com.oct.invoicesystem.domain.supplier.task;

import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupplierPerformanceTask {

    private final SupplierRepository supplierRepository;

    @Scheduled(cron = "${app.supplier.performance-task.cron:0 0 2 * * ?}") // Run at 2 AM every day
    @Transactional
    public void computeMetricsAndSuspendInactive() {
        log.info("Starting SupplierPerformanceTask to process metrics and auto-suspend inactive suppliers.");
        
        Instant inactiveThreshold = Instant.now().minus(365, ChronoUnit.DAYS);
        
        List<Supplier> inactiveSuppliers = supplierRepository.findAll().stream()
                .filter(s -> s.getStatus() == SupplierStatus.ACTIVE)
                .filter(s -> s.getUpdatedAt() != null && s.getUpdatedAt().isBefore(inactiveThreshold))
                .filter(s -> s.getDeletedAt() == null)
                .toList();
                
        for (Supplier supplier : inactiveSuppliers) {
            log.info("Auto-suspending supplier: {} (ID: {}) due to inactivity.", supplier.getCompanyName(), supplier.getId());
            supplier.setStatus(SupplierStatus.SUSPENDED);
            supplierRepository.save(supplier);
        }
        
        // Note: compute average processing times is a stub for foundation phase. 
        // Actual metrics calculation using invoices will be in Phase 9G.
        log.info("Supplier metrics calculation completed.");
    }
}
