package com.oct.invoicesystem.domain.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A selectable predefined rejection reason")
public record RejectionReasonOption(String code, String label) {}
