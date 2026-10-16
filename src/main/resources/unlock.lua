-- 比较线程标识与锁标识是否一致
if redis.call("get",KEY) == ARGV[1] then
--     释放锁
    return redis.call("del",KEY)
else
    return 0
end
