package com.flyrank.capstone.repository;
import java.util.UUID;
public interface WidgetSubmissionCount {
    UUID getWidgetId();
    String getTitle();
    Long getSubmissionCount();
}