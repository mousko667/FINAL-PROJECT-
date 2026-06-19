package com.oct.invoicesystem.domain.workflow.dto;

import com.oct.invoicesystem.domain.workflow.model.RejectionReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "{error.reject.code.required}")
    @Schema(description = "Predefined rejection reason code", example = "MONTANT_INCORRECT")
    private RejectionReasonCode reasonCode;

    @Schema(description = "Optional free-text detail (mandatory, min 10 chars, only when reasonCode = AUTRE)",
            example = "Le HT ne correspond pas au BDC")
    private String rejectionReason;
}
