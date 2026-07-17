package com.platform.ratelimiter.service;

import com.platform.ratelimiter.common.RateLimitAlgorithm;
import com.platform.ratelimiter.metrics.RateLimiterMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List<Long>> tokenBucketScript;
    private final RedisScript<List<Long>> slidingWindowScript;
    private final RateLimiterMetrics metrics;

    @Value("${rate-limiter.algorithm}")
    private String rateLimitAlgo;

    private String buildRedisKey(String clientId, String rateLimitAlgo) {
        return ("rate_limit:" + rateLimitAlgo.toLowerCase() + ":" + clientId);
    }

    public Mono<List<Long>> checkRateLimit(String clientId, long capacity, double refillRate, long windowLength) {
    
        String redisKey = buildRedisKey(clientId, rateLimitAlgo);
        List<String> keys = List.of(redisKey);
        RedisScript<List<Long>> script;
        List<String> args;
        if(RateLimitAlgorithm.TOKEN_BUCKET.name().equals(rateLimitAlgo)) {
            args = List.of(
                String.valueOf(System.currentTimeMillis()),  // current time in epoch millis, as String
                String.valueOf(capacity),  // capacity as String
                String.valueOf(refillRate)   // refillRate as String
            );
            script = tokenBucketScript;
        } 
        else if(RateLimitAlgorithm.SLIDING_WINDOW.name().equals(rateLimitAlgo)) {
            args = List.of(
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(capacity),
                String.valueOf(windowLength)
            ); //right now service is getting refillRate not windowLength so added it to filter and app yaml
            script = slidingWindowScript;
        }
        else {
            throw new IllegalArgumentException("Unknown algorithm: " + rateLimitAlgo);
        }
        
        // Use the injected Lua script bean
        // send KEYS and ARGV to Redis
        // run the script
        // take the first result
        // return Mono<List<Long>>
        long startTime = System.nanoTime();
        return redisTemplate.execute(script, keys, args).next()
                .doFinally(signalType ->
                        metrics.redisExecutionTimer().record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
                )
                .onErrorResume(exception -> 
                                    {
                                        log.error("Redis rate limiter failed, failing open", exception);
                                        metrics.recordRedisFailOpen();
                                        return Mono.just(List.of(1L,-1L));
                                    }
                                );
    }
}