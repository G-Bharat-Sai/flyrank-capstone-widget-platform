package com.flyrank.capstone.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubmissionResponse(
    UUID id,
    UUID widgetId,
    OffsetDateTime createdAt
) {}
