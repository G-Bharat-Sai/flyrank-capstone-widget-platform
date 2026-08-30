package com.flyrank.capstone.dto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public record WidgetResponse(
    UUID id,
    String type,
    String title,
    String description,
    List<WidgetFieldDto> fields,
    String buttonText,
    Map<String, Object> displayOptions,
    String webhookUrl,
    int version,
    String embedSnippet,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean requireProofOfWork,
    boolean requireDoubleOptIn
) {}