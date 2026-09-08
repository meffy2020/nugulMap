package com.neogulmap.neogul_map.dto.recommendation;

public sealed interface PlaceSearchArea permits PlaceSearchArea.CenterRadius, PlaceSearchArea.Bounds {

    record CenterRadius(double latitude, double longitude, int radiusMeters) implements PlaceSearchArea {
        public CenterRadius {
            requireLatitude(latitude);
            requireLongitude(longitude);
            if (radiusMeters < 0) {
                throw new IllegalArgumentException("radiusMeters must not be negative");
            }
        }
    }

    record Bounds(
            double minLatitude,
            double minLongitude,
            double maxLatitude,
            double maxLongitude
    ) implements PlaceSearchArea {
        public Bounds {
            requireLatitude(minLatitude);
            requireLatitude(maxLatitude);
            requireLongitude(minLongitude);
            requireLongitude(maxLongitude);
            if (minLatitude >= maxLatitude || minLongitude >= maxLongitude) {
                throw new IllegalArgumentException("bounds minimums must be smaller than maximums");
            }
        }
    }

    private static void requireLatitude(double latitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude is outside WGS84 range");
        }
    }

    private static void requireLongitude(double longitude) {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude is outside WGS84 range");
        }
    }
}
