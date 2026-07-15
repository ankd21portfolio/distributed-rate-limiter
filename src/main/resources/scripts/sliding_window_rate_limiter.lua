local key = KEYS[1]          
local current_time = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local window_length = tonumber(ARGV[3])

-- Remove requests outside the sliding window
local boundary = current_time - window_length - 1 -- - 1 so that the exact boundary time is inclusive to the window
redis.call('ZREMRANGEBYSCORE', key, '-inf', boundary)

local count = tonumber(redis.call('ZCARD', key))
local allowed = 0
local remaining = 0

if count >= capacity then
    allowed = 0
    remaining = 0
else    
    allowed = 1
    remaining = capacity - count - 1
    local member = current_time .. ':' .. (count + 1)
    redis.call('ZADD', key, current_time, member)
end

return {allowed, remaining}
