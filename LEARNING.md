# Distributed Rate Limiter: Interview Revision Notes

## 1. One-Line Project Pitch

Built a Redis-backed distributed token bucket rate limiter in Spring Boot WebFlux. A global `WebFilter` intercepts requests before controller execution, calls a reactive Redis service, and uses an atomic Lua script to decide whether to continue the request or return `429 Too Many Requests`.

## 2. Architecture Mental Model

```text
HTTP request
-> RateLimiterFilter
-> extract X-API-KEY, fallback to remote IP
-> build Redis key: rate_limit:{clientId}
-> RateLimiterService.checkRateLimit(...)
-> ReactiveRedisTemplate.execute(rateLimiterScript, keys, args)
-> Redis runs Lua script atomically
-> Lua returns [allowed, remainingTokens]
-> allowed: chain.filter(exchange)
-> rejected: set 429 + setComplete()
```

Filter vs endpoint:

```text
Filter = production turnstile, runs transparently before controllers.
Endpoint = manual check API, useful for debugging but clients could bypass it.
```

## 3. Why Redis Instead Of Local JVM Memory

In a horizontally scaled system, multiple app instances sit behind a load balancer. If each instance keeps its own `TokenBucket.java` state, requests can bypass limits by landing on different instances.

Failure mode:

```text
Request 1 -> Instance A -> local tokens drop
Request 2 -> Instance B -> separate local bucket still full
Result -> limiter bypassed
```

Fix:

```text
Move token state into shared Redis.
Every app instance reads/writes the same centralized state.
```

## 4. Local Variables To Redis Hash

Local scratchpad state:

```java
currentTokens
lastRefillTime
capacity
refillRate
```

Distributed Redis state:

```text
Redis key: rate_limit:user_123

Hash fields:
tokens       -> remaining bucket tokens
last_updated -> last processed timestamp in epoch millis
```

Dynamic inputs:

```text
capacity    -> passed from app config
refillRate  -> passed from app config
currentTime -> passed by Java on each request
```

## 5. Why Lua Exists

Without Lua, a race condition is possible:

```text
Instance A reads tokens = 1
Instance B reads tokens = 1
Instance A decrements to 0 and allows
Instance B also decrements to 0 and allows
Result: two requests pass with one token
```

Lua fix:

```text
Pack read -> calculate -> update -> return into one Redis Lua script.
Redis executes the script atomically from start to finish.
```

Interview answer:

> The application tier is reactive and concurrent, but state mutation is serialized inside Redis using an atomic Lua script. The script performs the check-and-set operation without interleaving, so two app instances cannot both consume the same token from a stale read.

Constraint:

```text
Lua must stay tiny: read hash, arithmetic, update hash, return result.
No heavy loops or long-running logic because Redis is single-threaded per command execution.
```

## 6.1 M4 Fail-Open Fallback

If Redis fails, we do not reject traffic at the gateway. The service returns `Mono.just(List.of(1L, -1L))` in the reactive fallback.

- `Mono.just(...)` creates the reactive fallback value immediately.
- `1L` means allow the request.
- `-1` is a sentinel for “Redis unavailable, bypass mode” instead of a real token count.
- This keeps the filter logic unchanged and preserves availability when the datastore is down.

CAP theorem note:

- We choose Availability over Consistency for this gateway path.
- In a transient Redis outage, failing open avoids a hard service outage even though some requests may bypass rate limits.
- That is the AP trade-off in a distributed system: keep the service responsive, accept weaker rate-limit consistency.

## 7. Reactive Redis Wiring

`RateLimiterService` asks for:

```java
ReactiveRedisTemplate<String, String>
```

Spring Boot provides:

```java
ReactiveStringRedisTemplate
```

Why it works:

```text
ReactiveStringRedisTemplate extends ReactiveRedisTemplate<String, String>
```

Memory hook:

```text
Service asks for parent type; Spring injects child object.
```

Script bean flow:

```text
RateLimiterConfig
-> @Bean rateLimiterScript()
-> loads scripts/rate_limiter.lua
-> registers RedisScript<List<Long>>
-> RateLimiterService receives it via constructor injection
```

Execution:

```java
redisTemplate.execute(rateLimiterScript, keys, args).next()
```

Meaning:

```text
Use Redis template
Run injected Lua script
Pass KEYS and ARGV
Take first emitted result
Return Mono<List<Long>>
```

## 7. Java To Lua Mapping

Java:

```java
keys = List.of(clientKey);
args = List.of(
    String.valueOf(System.currentTimeMillis()),
    String.valueOf(capacity),
    String.valueOf(refillRate)
);
```

Lua:

```lua
KEYS[1] = clientKey
ARGV[1] = currentTime
ARGV[2] = capacity
ARGV[3] = refillRate
```

Important detail:

```text
Redis ARGV values travel as strings.
Java uses String.valueOf(...)
Lua uses tonumber(...)
```

Result:

```lua
return { allowed, math.floor(current_tokens) }
```

Java receives:

```text
[1, 4]

1 -> allowed
4 -> remaining tokens
```

## 8. WebFlux Filter Layer

Why `WebFilter`:

```text
It intercepts WebFlux HTTP requests before controller handling.
This is the right production enforcement layer.
```

Why `@Order(-1)`:

```text
Spring sorts filters by ascending order.
Lower number runs earlier.
@Order(-1) makes the limiter run early in the request chain.
```

Important correction:

```text
WebFilter + @Component = participates in the global filter chain.
@Order only controls when it runs, not whether it runs.
```

Why `Mono<Void>`:

```text
The filter is not returning a response body object.
It returns an async completion signal:
- allowed -> continue with chain.filter(exchange)
- rejected -> complete response with setComplete()
```

## 9. `flatMap`, `map`, And `block`

`Mono<T>` means:

```text
Future async value, not the value itself.
```

Use `.map(...)` when returning a plain value:

```text
Mono<T> -> map(T -> R) -> Mono<R>
```

Use `.flatMap(...)` when returning another `Mono`:

```text
Mono<T> -> flatMap(T -> Mono<R>) -> Mono<R>
```

Filter needs `flatMap` because both branches return `Mono<Void>`:

```java
return rateLimiterService.checkRateLimit(...)
    .flatMap(result -> {
        if (allowed) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    });
```

Why no `.block()`:

```text
block() stops the Netty event-loop thread and waits for Redis.
That defeats non-blocking I/O and can starve the server under load.
In reactive code, describe what happens when the value arrives.
```

## 10. HTTP Behavior Verified

Config:

```yaml
rate-limiter:
  capacity: 3
  refill-rate: 0.0005
```

Meaning:

```text
0.0005 tokens/ms = 0.5 tokens/sec = 1 token every 2 seconds
```

Observed with same `X-API-KEY`:

```text
1st request -> 200, X-RateLimit-Remaining: 2
2nd request -> 200, X-RateLimit-Remaining: 1
3rd request -> 200, X-RateLimit-Remaining: 0
4th request -> 429, X-RateLimit-Remaining: 0
```

Why `200` with remaining `0` is valid:

```text
The request consumed the final token.
The next immediate request should be rejected.
```

Why a later request may pass after `0`:

```text
The bucket refills over elapsed time.
Also, internal tokens can be fractional while the header is floored.
```

If refill is set to `0` and the same user immediately gets `429`:

```text
Redis may still contain old state with tokens = 0.
Delete key: redis-cli DEL rate_limit:user1
```

## 11. Model Design Decisions

`RateLimitRequest`:

```text
Mutable DTO.
Uses no-arg constructor and setters so Jackson can deserialize JSON.
```

`RateLimitResponse`:

```text
Immutable response object.
Represents a completed rate-limit decision.
```

Header:

```text
X-RateLimit-Remaining lets clients and gateways inspect quota without parsing body JSON.
```

## 12. Dependency Injection

Pattern:

```java
@RequiredArgsConstructor
private final RateLimiterService rateLimiterService;
```

Why constructor injection:

```text
Explicit -> constructor shows required dependencies.
Immutable -> final dependency cannot be reassigned.
Testable -> unit tests can pass fake/mock dependencies directly.
Fail-fast -> object cannot be created without required dependencies.
```

`@RequiredArgsConstructor` vs field `@Autowired`:

```text
@RequiredArgsConstructor + final field:
- Lombok generates a constructor.
- Dependency is required at object creation time.
- Object cannot exist without that dependency.
- Good for production classes.

Field @Autowired:
- Spring creates the object first.
- Spring injects the field afterward using reflection.
- Dependency is less visible and cannot be final in the same clean way.
- Acceptable in Spring integration tests as a test harness shortcut.
```

Interview line:

> In production code, I prefer constructor injection because dependencies are explicit, immutable, and part of the object's creation contract. In integration tests, field `@Autowired` is acceptable because the test class is just asking Spring for a real bean from the application context.

Why `@Value` fields are not final:

```text
Field-based @Value injection happens after object construction.
final fields must be assigned during construction.
Use constructor binding or @ConfigurationProperties later for final config objects.
```

## 13. Spring Boot vs WebFlux

```text
Spring Boot = application startup, auto-configuration, bean creation, embedded server.
WebFlux = reactive web framework for HTTP handling with Mono/Flux.
Netty = default non-blocking server used by WebFlux.
```

Proof WebFlux is active:

```text
Startup log shows NettyWebServer, not TomcatWebServer.
```

## 14. Lombok

Lombok annotations used:

```text
@RequiredArgsConstructor
@Data
@NoArgsConstructor
@AllArgsConstructor
```

Interview framing:

```text
Lombok runs during compilation through annotation processing.
It generates normal Java bytecode such as constructors, getters, setters, equals, and hashCode.
It has no runtime reflection cost for these generated methods.
```

## 15. Sharp Interview Answers

Why distributed limiter?

> Local JVM buckets fail behind a load balancer because each instance has isolated memory. Redis centralizes token state so all instances enforce the same quota.

Why Lua?

> Lua makes the Redis read-calculate-update sequence atomic. Without it, two instances could read the same token count and both allow a request.

Why reactive Redis?

> The limiter sits on the request path, so blocking Redis I/O can starve request threads. Reactive Redis lets the app issue the command, release the event-loop thread, and resume when Redis responds.

Why filter instead of endpoint?

> A filter enforces automatically before controller logic. An endpoint requires clients to ask permission and can be bypassed.

Why `flatMap`?

> The Redis check returns a `Mono`, and the next action also returns a `Mono<Void>`. `flatMap` lets me choose the next async operation after the Redis result arrives.

Why no `block()`?

> `block()` turns async I/O into stop-and-wait behavior and can block Netty event-loop threads.

What does `setComplete()` do?

> It marks the HTTP response as finished. In the rejected branch, the filter sets status `429` and completes the response without calling the controller.

## 16. Current Project Status

Done:

```text
Lua token bucket script
ReactiveRedisTemplate wiring
RedisScript bean loading
Manual service endpoint
Filter-based production enforcement
YAML capacity/refill config
Manual verification of 200/429 behavior
Testcontainers Redis integration started
StepVerifier cold-start test passing
```

Next:

```text
StepVerifier tests:
- normal consume
- boundary last token
- burst over capacity
- refill after wait
```
