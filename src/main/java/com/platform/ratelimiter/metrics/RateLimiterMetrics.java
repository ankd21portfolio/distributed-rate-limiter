package com.platform.ratelimiter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class RateLimiterMetrics {
    private final Counter allowedRequests;
    private final Counter rejectedRequests;
    private final Counter redisFailOpenTotal;
    private final Timer redisExecutionTimer;

    public RateLimiterMetrics(MeterRegistry meterRegistry) {
        this.allowedRequests = meterRegistry.counter("rate_limiter_requests_allowed_total");
        this.rejectedRequests = meterRegistry.counter("rate_limiter_requests_rejected_total");
        this.redisFailOpenTotal = meterRegistry.counter("rate_limiter_redis_fail_open_total");
        this.redisExecutionTimer = meterRegistry.timer("rate_limiter_redis_execution");
    }

    public void recordAllowed() {
        allowedRequests.increment();
    }
    public void recordRejected() {
        rejectedRequests.increment();
    }
    public void recordRedisFailOpen() {
        redisFailOpenTotal.increment();
    }

    public Timer redisExecutionTimer() {
        return redisExecutionTimer;
    }
}
