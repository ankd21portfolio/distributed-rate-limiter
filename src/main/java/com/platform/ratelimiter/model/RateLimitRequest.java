package com.platform.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRequest {
    private String clientId;
    private long capacity;
    private double refillRate;
}