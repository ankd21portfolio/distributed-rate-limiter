package com.platform.ratelimiter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List<Long>> rateLimiterScript;

    public Mono<List<Long>> checkRateLimit(String clientKey, long capacity, double refillRate) {
        List<String> keys = List.of(clientKey);
        List<String> args = List.of(
            String.valueOf(System.currentTimeMillis()),  // current time in epoch millis, as String
            String.valueOf(capacity),  // capacity as String
            String.valueOf(refillRate)   // refillRate as String
        );

        // Use the injected Lua script bean
        // send KEYS and ARGV to Redis
        // run the script
        // take the first result
        // return Mono<List<Long>>
        return redisTemplate.execute(rateLimiterScript, keys, args).next()
                .onErrorResume(exception -> 
                                    {
                                        log.error("Redis rate limiter failed, failing open", exception);
                                        return Mono.just(List.of(1L,-1L));
                                    }
                                );
    }
}