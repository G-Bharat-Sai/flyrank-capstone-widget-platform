package com.flyrank.capstone.repository;

import com.flyrank.capstone.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    List<Submission> findAllByWidgetId(UUID widgetId);
    List<Submission> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    long countByOwnerIdAndCreatedAtAfter(UUID ownerId, OffsetDateTime since);
    long countByWidgetId(UUID widgetId);
}
