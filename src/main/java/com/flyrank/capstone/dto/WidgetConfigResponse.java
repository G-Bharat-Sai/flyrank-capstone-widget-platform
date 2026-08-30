package com.flyrank.capstone.dto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public record WidgetConfigResponse(
    UUID id,
    String type,
    String title,
    String description,
    List<WidgetFieldDto> fields,
    String buttonText,
    Map<String, Object> displayOptions,
    int version,
    boolean requireProofOfWork,
    boolean requireDoubleOptIn
) {}