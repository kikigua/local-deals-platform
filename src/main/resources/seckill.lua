---1、参数列表
--- 1.1优惠券id  ARGV[1] 就是调用方（Java）传给 Redis EVAL 命令的「第一个 arg 参数」
local voucherId = ARGV[1]
--- 1.2用户id
local userId = ARGV[2]
--- 1.3订单id
local orderId = ARGV[3]

---2、数据key
---2.1库存key
local stockKey = "seckill:stock:" .. voucherId
---2.2订单key
local orderKey = "seckill:order:" .. voucherId


---3、脚本业务
---3.1减库存，判断库存是否充足
if tonumber(redis.call("get", stockKey) or "0") <= 0 then
    return 1
end
---3.2判断用户是否下单
if redis.call("sismember", orderKey, userId) == 1 then
    --用户重复下单返回2
    return 2
end
---3.3扣库存，incrby stockKey -1
redis.call("incrby", stockKey, -1)
---3.4下单保存用户id
redis.call("sadd", orderKey, userId)
---3.5发送消息到队列中
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
---返回结果
return 0
