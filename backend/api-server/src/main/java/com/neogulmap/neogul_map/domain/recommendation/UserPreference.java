package com.neogulmap.neogul_map.domain.recommendation;

import com.neogulmap.neogul_map.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_preference")
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "personalization_enabled", nullable = false)
    private boolean personalizationEnabled;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Convert(converter = RecommendationTagSetConverter.class)
    @Column(name = "preferred_tags", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private Set<String> preferredTags = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Convert(converter = RecommendationTagSetConverter.class)
    @Column(name = "disliked_tags", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private Set<String> dislikedTags = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_level", length = 20)
    private BudgetLevel budgetLevel;

    @Column(name = "max_distance_meters")
    private Integer maxDistanceMeters;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void update(
            boolean personalizationEnabled,
            Set<String> preferredTags,
            Set<String> dislikedTags,
            BudgetLevel budgetLevel,
            Integer maxDistanceMeters,
            LocalDateTime updatedAt
    ) {
        this.personalizationEnabled = personalizationEnabled;
        this.preferredTags = new LinkedHashSet<>(preferredTags);
        this.dislikedTags = new LinkedHashSet<>(dislikedTags);
        this.budgetLevel = budgetLevel;
        this.maxDistanceMeters = maxDistanceMeters;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
}
