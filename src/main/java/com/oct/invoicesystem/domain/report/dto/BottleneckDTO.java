package com.oct.invoicesystem.domain.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Approval bottleneck information for a specific approval step.
 * Represents average approval duration for a step type (N1, N2, DAF) within a department.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BottleneckDTO {
    
    /** Department code (e.g., "DRH", "TECH") */
    private String departmentCode;
    
    /** Step order (1 for N1, 2 for N2, 3 for DAF) */
    private Integer stepOrder;
    
    /** Step type display name (e.g., "N1 Reviewer", "N2 Reviewer") */
    private String stepName;
    
    /** Average days spent in this approval step */
    private Double averageDays;
    
    /** Number of approval steps analyzed */
    private Long stepCount;
    
    /** Whether this step exceeds 3-business-day SLA */
    private Boolean bottleneck;
}
