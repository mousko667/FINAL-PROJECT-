package com.oct.invoicesystem.domain.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to reject an invoice")
public class RejectRequest {
    
    @NotBlank(message = "{error.rejection.reason.required}")
    @Size(min = 10, message = "{error.rejection.reason.min.length}")
    @Schema(description = "Mandatory reason for rejection (min 10 chars)", example = "Amount exceeds the approved budget")
    private String rejectionReason;
}
