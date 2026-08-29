package com.flyrank.capstone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record SubmissionRequest(
    @NotNull UUID widgetId,
    @NotNull Map<String, Object> fields,
    String honeypot,
    Long formRenderedAt
) {}
