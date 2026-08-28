package com.flyrank.capstone.dto;

import jakarta.validation.constraints.NotBlank;

public record WidgetFieldDto(
    @NotBlank String name,
    @NotBlank String label,
    @NotBlank String type,
    boolean required
) {}
