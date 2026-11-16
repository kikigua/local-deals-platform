--Kafka 发送失败时，撤销本次 Redis 预扣库存和一人一单标记
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId
local reservationKey = "seckill:reservation:" .. voucherId .. ":" .. userId

if redis.call("get", reservationKey) == orderId then
    redis.call("del", reservationKey)
    if redis.call("srem", orderKey, userId) == 1 then
        redis.call("incrby", stockKey, 1)
    end
end
return 0
