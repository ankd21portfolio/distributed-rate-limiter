package com.platform.ratelimiter.model;

public class RateLimitResponse {
    private final boolean allowed;
    private final long remainingTokens;
    private final String message;

    public RateLimitResponse(boolean allowed, long remainingTokens) {
        this.allowed = allowed;
        this.remainingTokens = remainingTokens;
        this.message = allowed ? "Rate limit allowed." : "Rate limit exceeded. Please try again later.";
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "RateLimitResponse{" +
                "allowed=" + allowed +
                ", remainingTokens=" + remainingTokens +
                ", message='" + message + '\'' +
                '}';
    }
}