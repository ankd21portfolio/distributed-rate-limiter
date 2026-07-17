# LEARNING.md

Interview revision notes for the Distributed Rate Limiter project.

---

# 1. High-Level Design

Flow

HTTP Request
→ RateLimiterFilter
→ Extract Client ID
→ RateLimiterService
→ Redis Lua Script
→ Allow / Reject
→ Controller (only if allowed)

State is NOT stored in the application.

State lives in Redis.

Therefore:

- Multiple application instances share the same rate limit.
- Application becomes stateless with respect to rate limiting.
- This is why the project is "distributed."

---

# 2. Why Redis?

- Very fast in-memory datastore.
- Atomic Lua execution.
- Shared across multiple application instances.
- Built-in data structures (Strings, Sorted Sets, Hashes, etc.).

---

# 3. Why Lua?

Without Lua:

Read
↓

Compute
↓

Write

Three separate operations.

Two concurrent requests could interleave.

Lua executes the entire decision atomically inside Redis.

One request finishes before the next begins.

No race conditions.

---

# 4. Why WebFilter?

Runs before every controller.

Advantages

- One implementation protects every endpoint.
- Controllers remain unaware of rate limiting.
- Easy to reuse.

---

# 5. Token Bucket

Stores

- token count
- last refill timestamp

Pros

- Small memory footprint
- Allows bursts
- Smooth average rate

Cons

Idle clients accumulate tokens.

After waiting, they may send a burst.

Good for

API gateways
Backend services
Most production systems

---

# 6. Sliding Window

Stores

One timestamp per request.

Redis data structure

Sorted Set

Flow

1. Remove expired timestamps.
2. Count remaining requests.
3. If count >= capacity → Reject.
4. Else add timestamp.

Pros

- Fair
- Prevents burst accumulation

Cons

Higher memory usage.

One Redis entry per request.

---

# 7. Redis Commands Used

ZADD

Add request timestamp.

---

ZCARD

Count requests currently inside the window.

---

ZREMRANGEBYSCORE

Remove expired timestamps.

Used for lazy cleanup.

---

ZRANGE ... WITHSCORES

Debug command.

Shows timestamps stored in Redis.

---

# 8. Lazy Cleanup

Redis does NOT automatically delete expired requests.

Cleanup happens only when another request arrives.

Reason

Avoid background cleanup work.

Very common production pattern.

---

# 9. Fail Open

Decision

If Redis is unavailable

↓

Allow request.

Reason

Availability > strict enforcement.

Alternative

Fail Closed

Reject everything.

Safer for security.

Risky for availability.

---

# 10. Why not Local Memory?

Each JVM would maintain its own counters.

Example

Instance A → 3 requests

Instance B → 3 requests

Client effectively gets 6 requests.

Redis solves this.

---

# 11. Things I'd Improve

- Use Redis TIME instead of JVM clock.
- Better unique Sorted Set members.
- Constructor injection instead of @Value.
- Separate request objects for each algorithm.
- Retry-After response header.
- Metrics (Micrometer).
- Docker Compose.
- GitHub Actions.

---

# 12. Interview Questions

Why WebFlux?

Why Redis?

Why Lua instead of MULTI/EXEC?

Why Sorted Set?

Why fail-open?

Why externalize state?

Why Sliding Window over Token Bucket?

How would you support millions of clients?

What happens if Redis crashes?

How would you shard Redis?

How would you prevent clock skew?

---

# 13. Things I Learned While Building

- Reactive Redis with ReactiveRedisTemplate.
- Redis Lua integration from Spring Boot.
- Testcontainers for Redis integration tests.
- Filter-based request interception.
- Sorted Set operations.
- Designing around trade-offs instead of perfect solutions.
- Every production decision has a cost.

# 14. Monitoring
### Observability Stack

Micrometer → Creates and maintains application metrics.
Actuator → Exposes application metrics and health endpoints.
Prometheus → Periodically scrapes and stores time-series metrics.
Grafana → Queries Prometheus to visualize dashboards.

- Micrometer creates and maintains live metric instruments inside the application.
- Micrometer knows the current value. Prometheus knows the history.
- Counter → cumulative value that only increases (requests, errors, failures).
- Gauge → current snapshot of a value that can increase or decrease (memory, queue size, connections).
- Timer → measures execution duration and request latency.

Logs vs Metrics

Logs → Explain what happened.
Metrics → Quantify how often and how well it's happening.

Prometheus Pull Model

Prometheus periodically calls:
/actuator/prometheus

The application never pushes metrics.

Least Privilege

Expose only required Actuator endpoints in production.

health
metrics
prometheus
Avoid exposing all endpoints (*).

```text
          Your Code
              │
              │ counter.increment()
              │ timer.record()
              ▼
┌───────────────────────────┐
│        Micrometer         │
│ Maintains live metrics    │
│ (Counter/Timer/Gauge)     │
└─────────────┬─────────────┘
              │
              ▼
┌───────────────────────────┐
│ Spring Boot Actuator      │
│ Exposes metrics over HTTP │
│ /actuator/prometheus      │
└─────────────┬─────────────┘
              │
              │ HTTP Scrape
              ▼
┌───────────────────────────┐
│       Prometheus          │
│ Scrapes & stores          │
│ time-series metrics       │
└─────────────┬─────────────┘
              │
              ▼
┌───────────────────────────┐
│         Grafana           │
│ Queries Prometheus        │
│ Builds dashboards         │
└───────────────────────────┘
```

- Spring Boot automatically instruments many frameworks (JVM, HTTP, Redis, thread pools, logging).
- /actuator is the discovery endpoint listing available management endpoints.
- /actuator/health reports application and dependency health(Redis, disk, SSL, etc.).
- /actuator/metrics lists available metrics. 
- /actuator/metrics/{name} shows one metric's current value. eg: count, max etc
- /actuator/prometheus exposes all Micrometer metrics in Prometheus text format.
- Prometheus periodically scrapes /actuator/prometheus; the application never pushes metrics.
- Expose only required Actuator endpoints in production (principle of least privilege).

### Custom metrics
Added custom Micrometer metrics:

- rate_limiter_requests_allowed_total
- rate_limiter_requests_rejected_total
- rate_limiter_redis_fail_open_total
- rate_limiter_redis_execution_seconds
  What's left?

## Spring Dependency Injection

- Spring creates `@Component`, `@Service`, `@Controller`, and `@Configuration` beans during application startup.
- Constructor parameters are automatically resolved from Spring's IoC container.
- Spring injects dependencies by calling the constructor with already-created beans.
- Constructor parameters are temporary; store them as fields only if the object is needed after construction.
- `MeterRegistry` is only needed to create `Counter`s during initialization, so it does not need to be stored as a field.

Example:

```java
@Component
public class RateLimiterMetrics {

    private final Counter allowedRequests;

    public RateLimiterMetrics(MeterRegistry meterRegistry) {
        allowedRequests = meterRegistry.counter("rate_limiter_requests_allowed_total");
    }
}
```

Flow:

Application starts
→ Spring creates MeterRegistry
→ Spring creates RateLimiterMetrics
→ Constructor creates Counter objects
→ Bean is ready for use throughout the application's lifetime

WebFilter
Every incoming request passes through the WebFilter before reaching the controller.

chain.filter(exchange)
→ passes the request to the next filter.
→ if there are no more filters, the request reaches the controller.

Returning exchange.getResponse().setComplete()
stops the chain immediately.
