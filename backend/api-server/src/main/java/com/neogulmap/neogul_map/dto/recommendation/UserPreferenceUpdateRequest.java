package com.neogulmap.neogul_map.dto.recommendation;

import com.neogulmap.neogul_map.domain.recommendation.BudgetLevel;

import java.util.Set;

public record UserPreferenceUpdateRequest(
        boolean personalizationEnabled,
        Set<String> preferredTags,
        Set<String> dislikedTags,
        BudgetLevel budgetLevel,
        Integer maxDistanceMeters
) {
}
