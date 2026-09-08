package com.neogulmap.neogul_map.dto;

import java.util.List;

public record LocationLetterNearbyResponse(
        List<LocationLetterResponse> letters,
        int count,
        int radiusMeters
) {
}
