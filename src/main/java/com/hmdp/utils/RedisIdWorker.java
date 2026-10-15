package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 基于Redis的全局唯一ID生成器
 * @author 13948
 * @date 2026/4/1
 */
@Component
public class RedisIdWorker {

    // 开始时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    //序列号位数
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成一个基于Redis的全局唯一ID
     * @param keyPrefix
     * @return
     */
    public long nextId(String keyPrefix) {
        //1. 生成时间戳
        long timestamp = LocalDateTime.now().toEpochSecond(java.time.ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        //2. 生成序列号
        //2.1、获取当前日期精确到天
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2、自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3. 组合（位运算）返回
        return timestamp << COUNT_BITS | count;
//        低位补零后，异或运算
//        timestamp = 123 → 二进制: 1111011
//        左移4位后:       11110110000
    }
}
