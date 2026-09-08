package com.neogulmap.neogul_map.service.recommendation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public final class KakaoLocalOutboundBulkhead {

    static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 8;
    static final int HARD_MAX_CONCURRENT_REQUESTS = 64;
    static final long DEFAULT_ACQUIRE_TIMEOUT_MILLIS = 50;
    static final long HARD_MAX_ACQUIRE_TIMEOUT_MILLIS = 250;

    private final int maxConcurrentRequests;
    private final long acquireTimeoutMillis;
    private final Semaphore permits;

    public KakaoLocalOutboundBulkhead(
            @Value("${external.kakao.bulkhead.max-concurrent-requests:8}") int maxConcurrentRequests,
            @Value("${external.kakao.bulkhead.acquire-timeout-millis:50}") long acquireTimeoutMillis
    ) {
        this.maxConcurrentRequests = Math.max(1, Math.min(HARD_MAX_CONCURRENT_REQUESTS, maxConcurrentRequests));
        this.acquireTimeoutMillis = Math.max(
                0,
                Math.min(HARD_MAX_ACQUIRE_TIMEOUT_MILLIS, acquireTimeoutMillis)
        );
        this.permits = new Semaphore(this.maxConcurrentRequests, true);
    }

    boolean tryAcquire() {
        try {
            return permits.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    void release() {
        permits.release();
    }

    int maxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    long acquireTimeoutMillis() {
        return acquireTimeoutMillis;
    }
}
