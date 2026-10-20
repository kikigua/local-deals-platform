package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PreDestroy;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
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
//    //阻塞队列
//    private BlockingQueue<VoucherOrder> blockingQueue = new ArrayBlockingQueue<>(1024 * 1024);
    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 新增这一行
    private volatile boolean running = true;

    /**
     * 初始化线程池：创建一个单线程的线程池，用于处理阻塞队列中的订单信息
     */
    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    // 新增这个方法
    @PreDestroy
    public void destroy() {
        running = false;
        SECKILL_ORDER_EXECUTOR.shutdownNow();
        log.info("秒杀订单消费线程已停止");
    }
    /**
     * 从阻塞队列中获取订单信息并创建订单
     */
    // 用于线程池处理的任务
    // 当初始化完毕后，就会去从阻塞队列中获取订单信息
//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    //1、获取阻塞队列中的订单信息
//                    VoucherOrder voucherOrder = blockingQueue.take();
//                    //2、创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    //1、获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    //判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，说明没有消息，继续下一轮循环
                        continue;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //如果获取成功，说明有消息，创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
                } catch (Exception e) {
                    if (!running){
                        break;
                    }
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (running) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    proxy.createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
                } catch (Exception e) {
                    if (!running){
                        break;
                    }
                    log.error("处理pendding订单异常", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * 属于哪个类？属于VoucherOrderHandler
      * 创建订单方法实现，使用Redisson实现分布式锁
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1、获取用户
        Long userId = voucherOrder.getUserId();
        //2、创建锁对象(使用锁工具类redisson创建)
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3、获取锁
        boolean isLock = lock.tryLock();
        //4、判断是否获取锁成功
        if (!isLock) {
            log.error("请勿重复下单！");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }
    /**
     * 创建订单方法实现，使用Redisson实现分布式锁
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5. 一人一单
        Long userId = voucherOrder.getUserId();
        //5.1、判断用户已经下过订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //5.2、判断是否存在订单
        if (count > 0) {
            log.error("用户已经下过订单！");
            return;
        }
        //6. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        //       UPDATE seckill_voucher
        //        SET stock = stock - 1
        //        WHERE voucher_id = ? AND stock > 0;
        if (!success) {
            log.error("库存不足！");
            return;
        }
        //7.4 保存订单到数据库
        save(voucherOrder);
    }

    /**
     * 抢购秒杀优惠券(基于stream消息队列优化后实现异步秒杀)
     * @param voucherId
     * @return
     */
    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1、执行lua脚本
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order:");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));
        //2、判断脚本执行结果是否为0，若为0代表脚本完美执行
        assert result != null;
        int r = result.intValue();
        if (r != 0) {
            //2.1、不为0，代表没有购买资格
            return Result.fail(result == 1 ? "库存不足！" : "请勿重复下单！");
        }
        //3、获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3、返回订单id
        return Result.ok(orderId);
    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1、执行lua脚本
//        Long userId = UserHolder.getUser().getId();
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString());
//        //2、判断脚本执行结果是否为0，若为0代表脚本完美执行
//        assert result != null;
//        int r = result.intValue();
//        if (r != 0) {
//            //2.1、不为0，代表没有购买资格
//            return Result.fail(result == 1 ? "库存不足！" : "请勿重复下单！");
//        }
//        //2.2、为0，代表有购买资格，把下单信息保存到阻塞队列
//        long orderId = redisIdWorker.nextId("order:");
//
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        //保存阻塞队列
//        blockingQueue.add(voucherOrder);
//        //3、获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //3、返回订单id
//        return Result.ok(orderId);
//    }
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


}
