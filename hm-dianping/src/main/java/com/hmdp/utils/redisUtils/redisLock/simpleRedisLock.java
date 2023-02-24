package com.hmdp.utils.redisUtils.redisLock;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

//version1 分布式锁的获取和释放
public class simpleRedisLock implements redisLock {

    private String businessName;

    private static final String KEY_PREFIX="lock:";

    //线程标识
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";

    private StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        ClassPathResource classPathResource = new ClassPathResource("lock.lua");
        String filename = classPathResource.getFilename();
        boolean exists = classPathResource.exists();
        System.out.println(exists);
        System.out.println(filename);
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public simpleRedisLock(String businessName, StringRedisTemplate redisTemplate) {
        this.businessName = businessName;
        this.redisTemplate = redisTemplate;
    }


    //获取锁
    @Override
    public boolean tryLock(long timeoutSec) {
        String threadID =ID_PREFIX+Thread.currentThread().getId();
        String key = KEY_PREFIX+businessName;
        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(key, threadID, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    //释放锁
//    @Override
//    public void unLock() {
//        String threadId =ID_PREFIX+Thread.currentThread().getId();
//        String id = redisTemplate.opsForValue().get(KEY_PREFIX + businessName);
//
//        //Notice
//        //FIXME: 优化 => 对应线程 删除 自己的锁
//        //FIXME: 判断锁标识 和 释放锁 的操作要 一起执行 要保证 原子性
//        if(threadId.equals(id)){
//            String key = KEY_PREFIX+businessName;
//            redisTemplate.delete(key);
//        }
//
//
//    }

    //Notice:释放锁
    @Override
    public void unLock() {
        String threadId =ID_PREFIX+Thread.currentThread().getId();
        String id = KEY_PREFIX + businessName;
        List<String> list = Collections.singletonList(id);

        redisTemplate.execute(UNLOCK_SCRIPT,list,threadId);
    }
}
