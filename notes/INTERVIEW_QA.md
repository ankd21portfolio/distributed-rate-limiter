# INTERVIEW_QA.md

Spoken answers for common interview questions about this project.

---

## Q1. Why Redis instead of an in-memory HashMap?

A local HashMap works only for a single application instance.

In a distributed deployment behind a load balancer, each instance would maintain its own counters, allowing clients to exceed the intended limit by sending requests to different instances.

Redis externalizes the rate-limit state so every application instance shares the same source of truth.

---

## Q2. Why call it a Distributed Rate Limiter?

Because the rate-limit state is shared across all application instances through Redis.

The application itself is stateless with respect to rate limiting.

Any instance can process any request while enforcing the same quota.

---

## Q3. Why use Lua?

Each request requires multiple Redis operations:

- Read state
- Compute decision
- Update state

Without Lua, concurrent requests could interleave these operations and violate the rate limit.

Lua executes the entire script atomically inside Redis, guaranteeing correctness.

---

## Q4. Why WebFilter instead of controller logic?

Rate limiting is a cross-cutting concern.

A WebFilter executes before controllers, protecting every endpoint without duplicating code.

Controllers remain focused only on business logic.

---

## Q5. Why Token Bucket?

Pros

- Small memory footprint
- Smooth average request rate
- Widely used in production

Cons

Idle clients accumulate tokens, allowing bursts.

---

## Q6. Why Sliding Window?

Sliding Window prevents burst accumulation by evaluating requests within a continuously moving time window.

It provides fairer request distribution than Token Bucket.

The trade-off is higher memory usage because every request timestamp is stored.

---

## Q7. Why does Sliding Window consume more memory?

Token Bucket stores only:

- Current token count
- Last refill timestamp

Sliding Window stores one timestamp per request.

If one million requests are active within the window, Redis stores one million entries.

---

## Q8. Why Sorted Sets?

Sorted Sets naturally keep request timestamps ordered.

This allows efficient removal of expired entries and efficient counting of requests still inside the active window.

---

## Q9. Why isn't Redis automatically deleting old timestamps?

Cleanup is performed lazily.

Whenever a new request arrives, the Lua script removes expired timestamps before evaluating the request.

This avoids unnecessary background work while keeping the implementation correct.

---

## Q10. Why fail-open?

If Redis becomes unavailable, rejecting every request could cause an outage.

The application instead allows traffic temporarily, prioritizing availability over strict enforcement.

For security-critical APIs, a fail-closed strategy might be more appropriate.

---

## Q11. Why use the application clock instead of Redis TIME?

For simplicity.

A production improvement would be using Redis TIME so every application instance shares a single authoritative clock and avoids clock-skew issues.

---

## Q12. What's one thing you'd improve first?

I'd replace the Sorted Set member generation.

Currently it uses a timestamp and request count, which can collide under concurrency.

I'd generate a collision-free unique member while still using the timestamp as the score.

---

## Q13. If traffic grows to hundreds of millions of users?

Possible improvements:

- Redis Cluster
- Sharding by client ID
- Metrics with Prometheus
- Retry-After header
- Redis TIME
- Better monitoring and alerting

---

## Q14. Biggest lessons from this project?

- Designing distributed systems is about trade-offs.
- Atomicity matters more than individual Redis commands.
- Stateless services scale better.
- Rate limiting belongs in infrastructure, not business logic.
- Correctness is more important than premature optimization.