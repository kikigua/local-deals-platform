package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息，优先从redis中查询
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //用互斥锁解决缓存击穿
        Shop shop = cacheClient.queryWithMutex(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //用逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //7、返回结果
        return Result.ok(shop);
    }

    //创建线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    /**
//     * 逻辑过期解决缓存击穿
//     * @param id
//     * @return
//     */
//    public Shop queryWithLogicalExpire(Long id) {
//        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
//        //1、从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
//        //2、判断是否命中
//        if (StrUtil.isBlank(shopJson)) {
//            //3、缓存中不存在，返回null
//            return null;
//        }
//        //4、判断是否命中
//        //4.1、命中，需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5、判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //5.1、未过期，返回商铺信息
//            return shop;
//        }
//        //5.2、过期，需要缓存重建
//        //6、缓存重建
//        //6.1、获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        //6.2、判断是否获取锁成功
//        if (isLock) {
//            //6.3、如果获取锁成功，则开启独立线程，进行缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    //6.4、线程池执行，实现缓存重建
//                    saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //6.5、释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//        //6.6、返回重建后的商铺信息
//        return shop;
//    }

//    /**
//     * 空值缓存解决缓存穿透：缓存空对象
//     * @param id
//     * @return
//     */
//    public Shop queryWithPassThrough(Long id) {
//        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
//        //1、从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
//        //2、判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3、存在，返回商铺信息
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //判断是否为空值
//        if (shopJson != null) {//换行等无用符号
//            return null;
//        }
//        //4、不存在，查询数据库
//        Shop shop = getById(id);
//        //5、不存在，返回错误
//        if (shop == null) {
//            //将空值写入redis：缓存空对象方式
//            stringRedisTemplate.opsForValue().set(cacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);//有效期短一点
//            //返回错误信息
//            return null;
//        }
//        //6、存在，写入redis
//        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //7、返回结果
//        return shop;
//    }

//    /**
//     * 互斥锁解决缓存击穿
//     * @param id
//     * @return
//     */
//    public Shop queryWithMutex(Long id) {
//        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
//        //1、从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
//        //2、判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3、存在，返回商铺信息
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //判断是否为空值
//        if (shopJson != null) {//换行等无用符号
//            return null;
//        }
//        //4、实现缓存重建
//        //4.1、获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            //4.2、判断是否获取成功
//            if (!isLock) {
//                //4.3、失败则休眠，并重试
//                    Thread.sleep(100);
//                return queryWithMutex(id);
//            }
//            //4.4、成功，根据id查询数据库
//            shop = getById(id);
//            //模拟重建的延迟
//            Thread.sleep(200);
//            //5、不存在，返回错误
//            if (shop == null) {
//                //将空值写入redis：缓存空对象方式
//                stringRedisTemplate.opsForValue().set(cacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);//有效期短一点
//                //返回错误信息
//                return null;
//            }
//            //6、存在，写入redis
//            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //7、释放锁
//            unlock(lockKey);
//        }
//        //8、返回结果
//        return shop;
//    }

//    /**
//     * 尝试获取锁
//     * @param key
//     * @return
//     */
//    private  boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//    /**
//     * 释放锁
//     * @param key
//     */
//    private  void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }
//    /**
//     * 设置逻辑过期时间，解决缓存击穿问题
//     * @param id
//     */
//    private void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
//        //1、查询商铺信息
//        Shop shop = getById(id);
//        //延迟
//        Thread.sleep(200);
//        //2、封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //3、保存到redis
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    /**
     * 更新商铺信息，优先更新redis
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1、更新数据库
        updateById(shop);
        //2、删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
