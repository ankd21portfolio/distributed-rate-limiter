package com.platform.ratelimiter.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.List;

@SpringBootTest
@Testcontainers
class RateLimiterServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:latest")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RateLimiterService rateLimiterService;

    @Test
    void coldStartAllowsRequestAndConsumesOneToken() {
        StepVerifier.create(rateLimiterService.checkRateLimit("test-user", 3, 0))
                .expectNext(List.of(1L, 2L))
                .verifyComplete();
    }

    @Test
    void normalConsumeDecrementsRemainingTokens() {
        StepVerifier.create(rateLimiterService.checkRateLimit("normal-user", 3, 0))
                .expectNext(List.of(1L, 2L))
                .verifyComplete();

        StepVerifier.create(rateLimiterService.checkRateLimit("normal-user", 3, 0))
                .expectNext(List.of(1L, 1L))
                .verifyComplete();
    }

    @Test
    void boundaryLastTokenIsStillAllowed() {
        StepVerifier.create(rateLimiterService.checkRateLimit("boundary-user", 1, 0))
                .expectNext(List.of(1L, 0L))
                .verifyComplete();
    }

    @Test
    void burstOverCapacityIsRejected() {
        StepVerifier.create(rateLimiterService.checkRateLimit("burst-user", 2, 0))
                .expectNext(List.of(1L, 1L))
                .verifyComplete();

        StepVerifier.create(rateLimiterService.checkRateLimit("burst-user", 2, 0))
                .expectNext(List.of(1L, 0L))
                .verifyComplete();

        StepVerifier.create(rateLimiterService.checkRateLimit("burst-user", 2, 0))
                .expectNext(List.of(0L, 0L))
                .verifyComplete();
    }

    @Test
    void refillAfterWaitAllowsRequestAgain() throws InterruptedException {
        StepVerifier.create(rateLimiterService.checkRateLimit("refill-user", 1, 0.01))
                .expectNext(List.of(1L, 0L))
                .verifyComplete();

        Thread.sleep(150);

        StepVerifier.create(rateLimiterService.checkRateLimit("refill-user", 1, 0.01))
                .expectNext(List.of(1L, 0L))
                .verifyComplete();
    }
}
