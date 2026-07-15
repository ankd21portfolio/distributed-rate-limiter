package com.platform.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RateLimiterConfig {

    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript<List<Long>> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket_rate_limiter.lua"));
        script.setResultType(List.class); // matches Lua script response: return { allowed, current_tokens }
        return (RedisScript<List<Long>>) (RedisScript<?>) script;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript<List<Long>> slidingWindowScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/sliding_window_rate_limiter.lua"));
        script.setResultType(List.class); // matches Lua script response: return { allowed, remaining }
        return (RedisScript<List<Long>>) (RedisScript<?>) script;   
    }
}