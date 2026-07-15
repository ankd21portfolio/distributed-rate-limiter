package com.platform.ratelimiter.controller;

import com.platform.ratelimiter.model.RateLimitRequest;
import com.platform.ratelimiter.model.RateLimitResponse;
import com.platform.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RateLimiterController {
    private final RateLimiterService rateLimiterService;

    // Deprecated 
    // @PostMapping("/check-limit")
    // public Mono<ResponseEntity<RateLimitResponse>> checkRateLimit(@RequestBody RateLimitRequest request) {
    //     return rateLimiterService.checkRateLimit(request.getClientId(), request.getCapacity(), request.getRefillRate())
    //             .map(result -> {
    //                 boolean isAllowed = result.get(0) == 1L;
    //                 long remainingTokens = result.get(1);
    //                 RateLimitResponse response = new RateLimitResponse(isAllowed, remainingTokens);
                    
    //                 if(isAllowed) {
    //                     return ResponseEntity.ok().header("X-RateLimit-Remaining", String.valueOf(remainingTokens)).body(response);
    //                 } else {
    //                     return ResponseEntity.status(429).header("X-RateLimit-Remaining", String.valueOf(remainingTokens)).body(response);
    //                 }
    //             });
    // }

    
    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("UP"));
    }
}