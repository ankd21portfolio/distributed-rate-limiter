package com.platform.ratelimiter.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import com.platform.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

@Component
@Order(-1) // Ensure this filter runs before other filters
@RequiredArgsConstructor
public class RateLimiterFilter implements WebFilter {
    
    @Value("${rate-limiter.capacity}")
    private long capacity;

    @Value("${rate-limiter.refill-rate}")
    private double refillRate;

    @Value("${rate-limiter.window-length}")
    private long windowLength;

    private final RateLimiterService rateLimiterService;

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. Extract Client Identifier (Production standard: fall back to IP if header is missing)
        String clientId = request.getHeaders().getFirst("X-API-KEY");
        if(clientId == null || clientId.isBlank()) {
            clientId = request.getRemoteAddress() != null ? 
                        request.getRemoteAddress().getAddress().getHostAddress() : "anonymous";

        }
        log.info("Incoming request: {} Client ID: {}", request.getPath(), clientId);

        return rateLimiterService.checkRateLimit(clientId, capacity, refillRate, windowLength)
                        .flatMap(result -> {
                            boolean isAllowed = result.get(0) == 1L;
                            long remainingTokens = result.get(1);
                            exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(remainingTokens));

                            if(isAllowed) {
                                return chain.filter(exchange);
                            }

                            //error
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            return exchange.getResponse().setComplete();
                        });
    }

}
