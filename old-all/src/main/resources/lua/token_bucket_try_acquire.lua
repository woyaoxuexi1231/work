-- ============================================
-- 分布式令牌桶 Lua 脚本
-- 功能：原子性地补充令牌并尝试扣减 1 个令牌
-- ============================================

-- KEYS[1] = bucket_key          桶的 Redis Key
-- ARGV[1] = max_tokens          最大令牌数（实际是毫令牌数，已乘以 SCALE）
-- ARGV[2] = refill_rate         每秒生成的令牌数（实际是毫令牌/秒）
-- ARGV[3] = scale               毫令牌放大系数（如 1000）
-- 返回值：剩余毫令牌数（>=0 表示获取成功，-1 表示失败）

-- ============================================
-- 分布式令牌桶 Lua 脚本
-- ============================================

---@diagnostic disable: undefined-global
---@type redis
local redis = redis

-- 或者使用 EmmyLua 的注解语法
---@class RedisAPI
---@field call fun(command: string, ...: any): any

-- KEYS[1] = bucket_key
-- ARGV[1] = max_tokens
-- ARGV[2] = refill_rate
-- ARGV[3] = scale


local bucket_key = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local scale = tonumber(ARGV[3])

-- 获取 Redis 当前毫秒时间
-- TIME 命令返回 [seconds, microseconds]
local time_arr = redis.call('TIME')
-- 毫秒数
local now_ms = tonumber(time_arr[1]) * 1000 + math.floor(tonumber(time_arr[2]) / 1000)

-- 读取桶状态（tokens: 当前毫令牌数, last_time: 上次更新时间）
local state = redis.call('HMGET', bucket_key, 'tokens', 'last_time')
local tokens = tonumber(state[1])
local last_time = tonumber(state[2])

-- 如果 key 不存在，初始化为满桶
if tokens == nil then
    tokens = max_tokens
    last_time = now_ms
    -- 使用 HSET 初始化，并单独设置过期时间
    redis.call('HSET', bucket_key, 'tokens', tokens, 'last_time', last_time)
end


-- 限制最大计算时间间隔（如最多计算1小时的令牌）
local MAX_INTERVAL_MS = 3600000 -- 1小时
local elapsed = math.min(math.max(0, now_ms - last_time), MAX_INTERVAL_MS)
local generated = elapsed * refill_rate
local new_tokens = math.min(tokens + generated, max_tokens)

-- 判断是否足够 1 个令牌（需要 scale 个毫令牌）
if new_tokens < scale then
    -- 令牌不足，只更新时间不扣减
    redis.call('HMSET', bucket_key, 'tokens', new_tokens, 'last_time', now_ms)
    return -1
end

-- 扣减 1 个令牌
local after_acquire = new_tokens - scale
redis.call('HMSET', bucket_key, 'tokens', after_acquire, 'last_time', now_ms)
return after_acquire
