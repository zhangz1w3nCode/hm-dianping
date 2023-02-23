package com.hmdp.utils.redisLock;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

//version1 分布式锁的获取和释放
public class simpleRedisLock implements redisLock {

    private String businessName;

    private static final String KEY_PREFIX="lock:";

    private StringRedisTemplate redisTemplate;

    public simpleRedisLock(String businessName, StringRedisTemplate redisTemplate) {
        this.businessName = businessName;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        long threadID = Thread.currentThread().getId();
        String key = KEY_PREFIX+businessName;
        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(threadID), timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    @Override
    public void unLock() {
        String key = KEY_PREFIX+businessName;
        redisTemplate.delete(key);
    }
}
