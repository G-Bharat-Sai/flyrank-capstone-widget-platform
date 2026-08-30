package com.flyrank.capstone.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
public record CreateWidgetRequest(
    @NotBlank String type,
    @NotBlank String title,
    String description,
    @NotEmpty @Valid List<WidgetFieldDto> fields,
    String buttonText,
    Map<String, Object> displayOptions,
    String webhookUrl,
    Boolean requireProofOfWork,
    Boolean requireDoubleOptIn
) {}