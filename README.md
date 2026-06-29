# Distributed Rate Limiter

A Redis-backed, non-blocking rate limiter built with Spring Boot WebFlux. The project uses a token bucket algorithm implemented as a Lua script so each rate-limit check is executed atomically inside Redis.

## What This Project Demonstrates

- Global request interception with Spring WebFlux `WebFilter`
- Non-blocking Redis access through `ReactiveRedisTemplate`
- Atomic token bucket updates with Redis Lua scripting
- Config-driven capacity and refill rate
- HTTP `429 Too Many Requests` rejection at the filter layer
- Manual rate-limit check endpoint for debugging and verification

## Tech Stack

- Java 21
- Spring Boot 3.5.x
- Spring WebFlux
- Spring Data Redis Reactive
- Redis
- Lua
- Gradle
- Lombok

## Architecture

```text
Incoming HTTP request
        ↓
RateLimiterFilter
        ↓
Extract X-API-KEY header or fallback IP
        ↓
RateLimiterService.checkRateLimit(...)
        ↓
ReactiveRedisTemplate.execute(luaScript, keys, args)
        ↓
Redis runs token bucket Lua script atomically
        ↓
Lua returns [allowed, remainingTokens]
        ↓
Filter either continues request or returns 429
```

## Core Components

### `RateLimiterFilter`

The production enforcement layer. It runs for incoming WebFlux requests, builds a Redis key from `X-API-KEY` or remote IP, calls the rate limiter service, and either:

- allows the request to continue with `chain.filter(exchange)`
- rejects the request with `HTTP 429`

It also adds:

```text
X-RateLimit-Remaining
```

### `RateLimiterService`

The Redis execution layer. It prepares `KEYS` and `ARGV`, then executes the Lua script reactively:

```java
redisTemplate.execute(rateLimiterScript, keys, args).next()
```

### `RateLimiterConfig`

Loads the Lua script from:

```text
src/main/resources/scripts/rate_limiter.lua
```

and registers it as a Spring `RedisScript<List<Long>>` bean.

### `RateLimiterController`

Provides two endpoints:

```text
POST /api/v1/check-limit
GET  /api/v1/health
```

`/check-limit` is useful for manually calling the service. `/health` is useful for testing filter-level enforcement because the endpoint itself does not call the rate limiter service.

## Configuration

`src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: ratelimiter

  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms

rate-limiter:
  capacity: 3
  refill-rate: 0.0005
```

The refill rate is tokens per millisecond.

```text
0.0005 tokens/ms = 0.5 tokens/sec = 1 token every 2 seconds
```

## Running Locally

Start Redis:

```bash
docker run --name redis-rate-limiter -p 6379:6379 redis:latest
```

If the container already exists:

```bash
docker start redis-rate-limiter
```

Run the app:

```bash
./gradlew bootRun
```

## Manual Filter Test

Use the health endpoint so the request is rate-limited only by the filter:

```bash
curl -i -H "X-API-KEY: user1" http://localhost:8080/api/v1/health
```

With `capacity: 3` and quick repeated requests, expected behavior:

```text
1st request → 200 OK, X-RateLimit-Remaining: 2
2nd request → 200 OK, X-RateLimit-Remaining: 1
3rd request → 200 OK, X-RateLimit-Remaining: 0
4th request → 429 Too Many Requests, X-RateLimit-Remaining: 0
```

`200 OK` with `X-RateLimit-Remaining: 0` is valid. It means the request consumed the final available token. The next immediate request should be rejected unless enough time has passed for refill.

## Manual Service Test Endpoint

```bash
curl -i -X POST http://localhost:8080/api/v1/check-limit \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "user1",
    "capacity": 3,
    "refillRate": 0.0005
  }'
```

Note: this endpoint is not ideal for filter-only testing because the global filter runs before the controller, and the controller also calls the rate limiter service.

## Redis State Reset During Local Testing

Redis stores token bucket state per client key:

```text
rate_limit:{clientId}
```

Reset one test user:

```bash
redis-cli DEL rate_limit:user1
```

Clear all local Redis data:

```bash
redis-cli FLUSHALL
```