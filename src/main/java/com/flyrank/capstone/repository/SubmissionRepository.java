package com.flyrank.capstone.repository;
import com.flyrank.capstone.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    List<Submission> findAllByWidgetId(UUID widgetId);
    List<Submission> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    long countByOwnerIdAndCreatedAtAfter(UUID ownerId, OffsetDateTime since);
    long countByWidgetId(UUID widgetId);
    long countByOwnerId(UUID ownerId);

    @Query(value = """
            SELECT w.id AS widgetId, w.title AS title, COUNT(s.id) AS submissionCount
            FROM widgets w
            LEFT JOIN submissions s ON s.widget_id = w.id
            WHERE w.owner_id = :ownerId
            GROUP BY w.id, w.title
            ORDER BY submissionCount DESC
            """, nativeQuery = true)
    List<WidgetSubmissionCount> countSubmissionsPerWidget(@Param("ownerId") UUID ownerId);

    @Query(value = """
            SELECT geo_country AS country, COUNT(*) AS count
            FROM submissions
            WHERE owner_id = :ownerId AND geo_country IS NOT NULL
            GROUP BY geo_country
            ORDER BY count DESC
            LIMIT 10
            """, nativeQuery = true)
    List<CountryCount> countSubmissionsByCountry(@Param("ownerId") UUID ownerId);

    @Query(value = """
            SELECT DATE_TRUNC('day', created_at)::date AS day, COUNT(*) AS count
            FROM submissions
            WHERE owner_id = :ownerId AND created_at >= :since
            GROUP BY DATE_TRUNC('day', created_at)
            ORDER BY day
            """, nativeQuery = true)
    List<DailyCount> countSubmissionsPerDay(@Param("ownerId") UUID ownerId, @Param("since") OffsetDateTime since);
}