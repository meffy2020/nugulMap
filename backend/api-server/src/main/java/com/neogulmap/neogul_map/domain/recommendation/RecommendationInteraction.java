package com.neogulmap.neogul_map.domain.recommendation;

import com.neogulmap.neogul_map.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "recommendation_interaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rec_interaction_event",
                columnNames = {"user_id", "request_id", "item_type", "provider", "item_id", "event_type"}
        )
)
public class RecommendationInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 40)
    private RecommendationItemType itemType;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(name = "item_id", nullable = false, length = 191)
    private String itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private RecommendationEventType eventType;

    @Column(nullable = false, length = 40)
    private String intent;

    @Column(name = "context_region", length = 100)
    private String contextRegion;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void initializeOccurredAt() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
