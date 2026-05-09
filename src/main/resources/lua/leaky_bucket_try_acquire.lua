-- ============================================
-- 分布式漏桶 Lua 脚本
-- 功能：原子性地漏出已处理请求并尝试放入 1 个新请求
-- ============================================

---@diagnostic disable: undefined-global
---@type redis
local redis = redis



local bucket_key = KEYS[1]
local capacity   = tonumber(ARGV[1]) -- 桶容量, 这里相当于水桶一共有多大
local leak_rate  = tonumber(ARGV[2]) -- 漏出速率 这个相当于水龙头，水龙头出水量只有这么大
local scale      = tonumber(ARGV[3]) -- 放大系数（如 1000）

-- 获取当前时间（毫秒）
local time_arr   = redis.call('TIME')
local now_ms     = tonumber(time_arr[1]) * 1000 + math.floor(tonumber(time_arr[2]) / 1000)

-- 读取桶状态
local state      = redis.call('HMGET', bucket_key, 'water', 'last_time')
-- 水桶当前数量
local water      = tonumber(state[1])
-- 上一次请求的时间
local last_time  = tonumber(state[2])

-- 初始化：空桶
if water == nil then
    water = 0 -- 当前没有水
    last_time = now_ms
    redis.call('HSET', bucket_key, 'water', water, 'last_time', last_time)
end

-- 计算漏出量，这里要根据实际情况来，我这里只是假定我的场景一个小时肯定全漏完了，然后避免 elapsed * leak_rate 报错
local MAX_INTERVAL_MS = 60 * 60 * 1000 -- 1 小时
-- 计算当前毫秒数！
local elapsed = math.min(math.max(0, now_ms - last_time), MAX_INTERVAL_MS)

local leaked = elapsed * leak_rate / scale;
-- 计算出剩余毫米水
local new_water = math.max(0, water - leaked)

-- 判断是否还能再加入 1 个请求（加水 scale 滴）
if new_water + scale > capacity then
    -- 桶已满，只更新水量和时间（不加水）
    redis.call('HMSET', bucket_key, 'water', new_water, 'last_time', now_ms)
    return 0 -- 拒绝
end

-- 放入请求
local final_water = new_water + scale
redis.call('HMSET', bucket_key, 'water', final_water, 'last_time', now_ms)
return 1 -- 允许
