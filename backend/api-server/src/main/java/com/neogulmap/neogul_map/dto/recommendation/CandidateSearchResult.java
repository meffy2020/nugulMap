package com.neogulmap.neogul_map.dto.recommendation;

import java.util.List;
import java.util.Objects;

public record CandidateSearchResult(
        Status status,
        PlaceProvider provider,
        List<PlaceCandidate> candidates,
        int page,
        int size,
        boolean lastPage
) {
    public enum Status {
        SUCCESS,
        UNCONFIGURED,
        UPSTREAM_ERROR
    }

    public CandidateSearchResult {
        status = Objects.requireNonNull(status, "status");
        provider = Objects.requireNonNull(provider, "provider");
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("page and size must be positive");
        }
        if (status != Status.SUCCESS && !candidates.isEmpty()) {
            throw new IllegalArgumentException("non-success result must not contain candidates");
        }
    }

    public static CandidateSearchResult success(
            PlaceProvider provider,
            List<PlaceCandidate> candidates,
            int page,
            int size,
            boolean lastPage
    ) {
        return new CandidateSearchResult(Status.SUCCESS, provider, candidates, page, size, lastPage);
    }

    public static CandidateSearchResult unconfigured(PlaceProvider provider, int page, int size) {
        return new CandidateSearchResult(Status.UNCONFIGURED, provider, List.of(), page, size, true);
    }

    public static CandidateSearchResult upstreamError(PlaceProvider provider, int page, int size) {
        return new CandidateSearchResult(Status.UPSTREAM_ERROR, provider, List.of(), page, size, true);
    }
}
