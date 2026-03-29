package com.oct.invoicesystem.domain.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to approve an invoice or step")
public class ApprovalRequest {
    @Schema(description = "Optional comment from the approver", example = "Looks good")
    private String comment;
}
