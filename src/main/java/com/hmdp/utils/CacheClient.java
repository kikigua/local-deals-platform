package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Description: 缓存工具类
 * @Author: 墨守成规
 * @Date: 2023/8/10 22:20
 */
@Component
@Slf4j
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * @Description: 设置逻辑过期缓存
     * @Author: 墨守成规
     * @Date: 2023/8/10 22:20
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    /**
     * 空值缓存解决缓存穿透：缓存空对象
     *
     * @param id
     * @return
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,Long time, TimeUnit unit) {
        String cacheKey = keyPrefix + id;
        //1、从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        //2、判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3、存在，返回商铺信息
            R shop = JSONUtil.toBean(json, type);
            return shop;
        }
        //判断是否为空值
        if (json != null) {//换行等无用符号
            return null;
        }
        //4、不存在，查询数据库
        R r = dbFallback.apply(id);
        //5、不存在，返回错误
        if (r == null) {
            //将空值写入redis：缓存空对象方式
            stringRedisTemplate.opsForValue().set(cacheKey, "", time, unit);//有效期短一点
            //返回错误信息
            return null;
        }
        //6、存在，写入redis
        this.set(cacheKey, r, time, unit);
        //7、返回结果
        return r;
    }
    /**
     * @Description: 逻辑过期解决缓存击穿
     * @Author: 墨守成规
     * @Date: 2023/8/10 22:20
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String cacheKey = keyPrefix + id;
        //1、从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        //2、判断是否命中
        if (StrUtil.isBlank(json)) {
            //3、缓存中不存在，返回null
            return null;
        }
        //4、判断是否命中
        //4.1、命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5、判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1、未过期，返回商铺信息
            return r;
        }
        //5.2、过期，需要缓存重建
        //6、缓存重建
        //6.1、获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2、判断是否获取锁成功
        if (isLock) {
            //6.3、如果获取锁成功，则开启独立线程，进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //6.4、线程池执行，实现缓存重建
                    R r1 = dbFallback.apply(id);
                    this.set(cacheKey, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //6.5、释放锁
                    unlock(lockKey);
                }
            });
        }
        //6.6、返回重建后的商铺信息
        return r;
    }
    /**
     * @Description: 互斥锁解决缓存击穿
     * @Author: 墨守成规
     * @Date: 2023/8/10 22:20
     */
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String cacheKey = keyPrefix + id;
        //1、从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
        //2、判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3、存在，返回商铺信息
            R r = JSONUtil.toBean(shopJson, type);
            return r;
        }
        //判断是否为空值
        if (shopJson != null) {//换行等无用符号
            return null;
        }
        //4、实现缓存重建
        //4.1、获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2、判断是否获取成功
            if (!isLock) {
                //4.3、失败则休眠，并重试
                Thread.sleep(100);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            //4.4、成功，根据id查询数据库
            r = dbFallback.apply(id);
            //模拟重建的延迟
            Thread.sleep(200);
            //5、不存在，返回错误
            if (r == null) {
                //将空值写入redis：缓存空对象方式
                stringRedisTemplate.opsForValue().set(cacheKey, "", time, unit);//有效期短一点
                //返回错误信息
                return null;
            }
            //6、存在，写入redis
            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(r), time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7、释放锁
            unlock(lockKey);
        }
        //8、返回结果
        return r;
    }
    /**
     * @Description: 尝试获取锁
     * @Author: 墨守成规
     * @Date: 2023/8/10 22:20
     */
    private  boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    /**
     * @Description: 释放锁
     * @Author: 墨守成规
     * @Date: 2023/8/10 22:20
     */
    private  void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
