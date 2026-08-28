package com.flyrank.capstone.dto;
import java.util.UUID;
public record WidgetStatDto(UUID widgetId, String title, long submissionCount) {}