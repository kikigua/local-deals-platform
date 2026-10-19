package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;
    // 解锁的lua脚本,初始化
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 抢购秒杀优惠券(优化后)
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1、执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        //2、判断脚本执行结果是否为0，若为0代表脚本完美执行
        assert result != null;
        int r = result.intValue();
        if (r != 0) {
            //2.1、不为0，代表没有购买资格
            return Result.fail(result == 1 ? "库存不足！" : "请勿重复下单！");
        }
        //2.2、为0，代表有购买资格，把下单信息保存到阻塞队列
        long orderId = redisIdWorker.nextId("order:");
        //TODO 保存订单信息到阻塞队列

        //3、返回订单id
        return Result.ok(orderId);
    }
//    /**
//     * 抢购秒杀优惠券
//     * @param voucherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1. 查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2. 判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始！");
//        }
//        //3. 判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束！");
//        }
//        //4. 判断库存是否充足
//        if (voucher.getStock() <= 0) {
//            return Result.fail("库存不足！");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //锁
//        //创建锁对象(手动创建)
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
//        //创建锁对象(使用锁工具类redisson创建)
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //尝试获取锁
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("请勿重复下单！");
//        }
//        try {
//            //获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock();
//        }
//    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5. 一人一单
        Long userId = UserHolder.getUser().getId();
        //5.1、判断用户已经下过订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //5.2、判断是否存在订单
        if (count > 0) {
            return Result.fail("用户已经下过订单！");
        }
        //6. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
    //       UPDATE seckill_voucher
    //        SET stock = stock - 1
    //        WHERE voucher_id = ? AND stock > 0;
        if (!success) {
            return Result.fail("库存不足！");
        }
        //7. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1 生成订单id
        long orderId = redisIdWorker.nextId("order:");
        voucherOrder.setId(orderId);
        //7.2 获取用户id
        voucherOrder.setUserId(userId);
        //7.3 获取优惠券id
        voucherOrder.setVoucherId(voucherId);
        //7.4 保存订单到数据库
        save(voucherOrder);
        //8. 返回订单id
        return Result.ok(orderId);
        }
}
