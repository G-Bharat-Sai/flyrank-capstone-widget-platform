package com.flyrank.capstone.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "widget_id", nullable = false)
    private UUID widgetId;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;
    @Column(name = "geo_country", length = 100)
    private String geoCountry;
    @Column(name = "geo_city", length = 100)
    private String geoCity;
    @Column(name = "geo_provider_used", length = 20)
    private String geoProviderUsed;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}