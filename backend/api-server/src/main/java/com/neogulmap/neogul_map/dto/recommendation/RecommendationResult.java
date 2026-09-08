package com.neogulmap.neogul_map.dto.recommendation;

import java.util.List;

public record RecommendationResult(List<Item> items, int eligibleCandidateCount) {
    public RecommendationResult {
        items = List.copyOf(items == null ? List.of() : items);
        if (eligibleCandidateCount < items.size()) {
            throw new IllegalArgumentException("eligibleCandidateCount must cover returned items");
        }
    }

    public record Item(
            PlaceCandidate candidate,
            double score,
            List<RecommendationReasonCode> reasonCodes
    ) {
        public Item {
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        }
    }
}
