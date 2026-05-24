-- Atomic token-bucket rate limiter.
-- KEYS[1] = bucket key
-- ARGV[1] = capacity            (max tokens)
-- ARGV[2] = refillTokens        (tokens added per period)
-- ARGV[3] = refillPeriodMillis  (length of a refill period)
-- ARGV[4] = nowMillis           (current time)
-- Returns 1 if a token was granted, 0 if the bucket was empty.

local capacity = tonumber(ARGV[1])
local refillTokens = tonumber(ARGV[2])
local refillPeriod = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1])
local ts = tonumber(state[2])

if tokens == nil then
    tokens = capacity
    ts = now
end

-- Refill based on elapsed time since the last update.
local elapsed = now - ts
if elapsed > 0 then
    local earned = (elapsed / refillPeriod) * refillTokens
    tokens = math.min(capacity, tokens + earned)
    ts = now
end

local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', ts)

-- Expire idle buckets after they would have fully refilled (+1s slack) to avoid leaking keys.
local ttlMillis = math.ceil((capacity / refillTokens) * refillPeriod) + 1000
redis.call('PEXPIRE', KEYS[1], ttlMillis)

return allowed
