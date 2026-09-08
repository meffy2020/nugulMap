package com.neogulmap.neogul_map.dto.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.BudgetLevel;
import com.neogulmap.neogul_map.domain.recommendation.UserPreference;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record UserPreferenceResponse(
        boolean personalizationEnabled,
        Set<String> preferredTags,
        Set<String> dislikedTags,
        BudgetLevel budgetLevel,
        Integer maxDistanceMeters,
        LocalDateTime updatedAt
) {
    public static UserPreferenceResponse defaults() {
        return new UserPreferenceResponse(false, Set.of(), Set.of(), null, null, null);
    }

    public static UserPreferenceResponse from(UserPreference preference) {
        return new UserPreferenceResponse(
                preference.isPersonalizationEnabled(),
                Collections.unmodifiableSet(new LinkedHashSet<>(preference.getPreferredTags())),
                Collections.unmodifiableSet(new LinkedHashSet<>(preference.getDislikedTags())),
                preference.getBudgetLevel(),
                preference.getMaxDistanceMeters(),
                preference.getUpdatedAt()
        );
    }
}
