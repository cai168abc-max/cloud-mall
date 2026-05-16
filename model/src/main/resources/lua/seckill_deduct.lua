-- 秒杀库存扣减Lua脚本
-- KEYS[1]: 库存key
-- KEYS[2]: 售罄标记key
-- ARGV[1]: 扣减数量
-- ARGV[2]: 售罄标记过期时间（秒）

local stockKey = KEYS[1]
local soldOutKey = KEYS[2]
local quantity = tonumber(ARGV[1])
local expireTime = tonumber(ARGV[2])

-- 检查是否已售罄
if redis.call('EXISTS', soldOutKey) == 1 then
    return -1
end

-- 获取当前库存
local currentStock = tonumber(redis.call('GET', stockKey) or '0')

-- 检查库存是否充足
if currentStock < quantity then
    -- 设置售罄标记
    redis.call('SET', soldOutKey, '1', 'EX', expireTime)
    return -2
end

-- 扣减库存
local newStock = redis.call('DECRBY', stockKey, quantity)

-- 如果扣减后库存为0，设置售罄标记
if newStock == 0 then
    redis.call('SET', soldOutKey, '1', 'EX', expireTime)
end

return newStock
