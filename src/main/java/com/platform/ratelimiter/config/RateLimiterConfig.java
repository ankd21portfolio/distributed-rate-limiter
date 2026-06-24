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
    public RedisScript<List<Long>> rateLimiterScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        
        // Tells Spring to locate the file in: src/main/resources/scripts/rate_limiter.lua
        script.setLocation(new ClassPathResource("scripts/rate_limiter.lua"));
        
        // Must match your Lua table output: return { allowed, current_tokens }
        script.setResultType(List.class);
        
        return (RedisScript<List<Long>>) (RedisScript<?>) script;
    }
}