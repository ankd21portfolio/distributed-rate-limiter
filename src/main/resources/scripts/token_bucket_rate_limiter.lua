-- Block 1: Initialize Keyspace and Arguments
local key = KEYS[1]             -- The Redis Hash tracking this specific client/IP
local current_time = tonumber(ARGV[1]) -- Current timestamp in epoch milliseconds
local capacity = tonumber(ARGV[2])     -- Max bucket capacity
local refill_rate = tonumber(ARGV[3])  -- Tokens added per millisecond

-- Block 2: Fetch Current State
local rate_limit_state = redis.call('HMGET', key, 'tokens', 'last_updated')
local last_tokens = tonumber(rate_limit_state[1])
local last_updated = tonumber(rate_limit_state[2])

local current_tokens, elapsed_time, tokens_to_add

-- If the key doesn't exist yet, initialize a full bucket
if last_updated == nil or last_tokens == nil then
    current_tokens = capacity
    last_updated = current_time
else
    -- Lazy Refill
    elapsed_time = current_time - last_updated
    if elapsed_time > 0 then
        tokens_to_add = elapsed_time * refill_rate
        current_tokens = math.min(capacity, last_tokens + tokens_to_add)
        last_updated = current_time
    else
        current_tokens = last_tokens -- No time has passed, so tokens refill is not needed
    end
end

-- Evaluate Request (Consume 1 token if available)
local allowed = 0
if current_tokens >= 1 then
    current_tokens = current_tokens - 1
    -- min(capacity, tokens + refill) prevents burst exploitation after idle periods
    redis.call('HMSET', key, 'tokens', current_tokens, 'last_updated', last_updated)
    allowed = 1
end

-- Return both the decision and remaining tokens for downstream actionable headers
return { allowed, math.floor(current_tokens) }
