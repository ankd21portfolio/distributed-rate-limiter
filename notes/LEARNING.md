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