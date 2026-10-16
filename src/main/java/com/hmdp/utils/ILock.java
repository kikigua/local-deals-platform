package com.hmdp.utils;

/**
 * @Description:redis实现分布式锁
 * @Author: 咖啡
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 锁的过期时间
     * @return true代表获取锁成功；false代表获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
