package com.oct.invoicesystem.domain.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookCreateRequest {

    @NotBlank(message = "webhook.name.required")
    @Size(min = 1, max = 100, message = "webhook.name.size")
    private String name;

    @NotBlank(message = "webhook.url.required")
    @Size(min = 10, max = 1000, message = "webhook.url.size")
    private String url;

    @NotEmpty(message = "webhook.events.required")
    private List<String> events;
}
