package com.platform.ratelimiter.filter;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-1) // Ensure this filter runs before other filters
public class RateLimiterFilter implements WebFilter {

    private final ReactiveStringRedisTemplate redisTemplate;

    // Constructor injection for our non-blocking Redis template
    public RateLimiterFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. Extract Client Identifier (Production standard: fall back to IP if header is missing)
        String clientId = request.getHeaders().getFirst("X-API-KEY");
        if(clientId == null || clientId.isBlank()) {
            clientId = request.getRemoteAddress() != null ? 
                        request.getRemoteAddress().getAddress().getHostAddress() : "anonymous";

        }

        // 2. Formulate our unique state tracking key for Redis
        String redisKey = "rate_limit:" + clientId;

        // 3. Temporary Architectural Placeholder
        // Right now, we automatically approve everything so we can trace the pipeline layout.
        // In our next block, this hardcoded true transforms into our atomic Lua script call.
        boolean isAllowed = true; // Placeholder for Redis-based rate limit check
        if(isAllowed) {
            // If allowed, continue processing the request
            return chain.filter(exchange);
        } else {
            // If not allowed, respond with 429 Too Many Requests
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        ////////////////

    }

    @Override
    public int getOrder() {
        // Setting highest priority so rate limiting runs before routing or authentication filters cost resource overhead
        return Ordered.HIGHEST_PRECEDENCE;
    }
}