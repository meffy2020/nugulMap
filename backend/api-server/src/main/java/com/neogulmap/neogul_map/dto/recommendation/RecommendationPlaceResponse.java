package com.neogulmap.neogul_map.dto.recommendation;

import java.time.Instant;
import java.util.List;

public record RecommendationPlaceResponse(
        String requestId,
        String modelVersion,
        RecommendationDataStatus dataStatus,
        RecommendationPersonalizationMode personalizationMode,
        RecommendationIntent intent,
        int eligibleCandidateCount,
        Instant generatedAt,
        List<Item> items
) {
    public RecommendationPlaceResponse {
        items = List.copyOf(items == null ? List.of() : items);
    }

    public record Item(
            String id,
            PlaceProvider provider,
            String name,
            String category,
            String address,
            double latitude,
            double longitude,
            Integer distanceMeters,
            double score,
            List<RecommendationReasonCode> reasonCodes,
            String sourceUrl
    ) {
        public Item {
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        }

        public static Item from(RecommendationResult.Item rankedItem) {
            PlaceCandidate candidate = rankedItem.candidate();
            return new Item(
                    candidate.id(),
                    candidate.provider(),
                    candidate.name(),
                    candidate.category(),
                    candidate.address(),
                    candidate.latitude(),
                    candidate.longitude(),
                    candidate.distanceMeters(),
                    rankedItem.score(),
                    rankedItem.reasonCodes(),
                    candidate.placeUrl()
            );
        }
    }
}
