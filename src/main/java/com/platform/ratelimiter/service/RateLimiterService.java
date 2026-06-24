package com.platform.ratelimiter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List<Long>> rateLimiterScript;

    public Mono<List<Long>> checkRateLimit(String clientKey, long capacity, double refillRate) {
        List<String> keys = List.of(clientKey);
        List<String> args = List.of(
            String.valueOf(System.currentTimeMillis()),  // current time in epoch millis, as String
            String.valueOf(capacity),  // capacity as String
            String.valueOf(refillRate)   // refillRate as String
        );

        return redisTemplate.execute(rateLimiterScript, keys, args).next();
    }
}