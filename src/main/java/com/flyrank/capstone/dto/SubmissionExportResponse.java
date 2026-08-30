package com.flyrank.capstone.dto;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
public record SubmissionExportResponse(
    UUID id,
    UUID widgetId,
    Map<String, Object> fields,
    OffsetDateTime createdAt,
    boolean confirmed,
    OffsetDateTime confirmedAt,
    boolean consentGiven,
    OffsetDateTime consentAt,
    String geoCountry,
    String geoCity
) {}