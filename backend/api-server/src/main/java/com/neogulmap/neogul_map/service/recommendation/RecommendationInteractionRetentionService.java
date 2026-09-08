package com.neogulmap.neogul_map.service.recommendation;

import com.neogulmap.neogul_map.repository.recommendation.RecommendationInteractionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class RecommendationInteractionRetentionService {

    private final RecommendationInteractionRepository repository;
    private final Clock clock;

    @Value("${app.recommendations.interaction-retention-days:180}")
    private long retentionDays = 180;

    @Autowired
    public RecommendationInteractionRetentionService(RecommendationInteractionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    RecommendationInteractionRetentionService(
            RecommendationInteractionRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.recommendations.interaction-purge-cron:0 15 4 * * *}", zone = "UTC")
    @Transactional
    public long purgeExpiredInteractions() {
        long boundedRetentionDays = Math.max(1, retentionDays);
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .minusDays(boundedRetentionDays);
        return repository.deleteExpiredBefore(cutoff);
    }
}
