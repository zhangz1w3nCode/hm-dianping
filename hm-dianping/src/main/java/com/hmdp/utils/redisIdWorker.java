package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.hmdp.utils.RedisConstants.*;

//使用redis 完成全局id生成器
@Component
public class redisIdWorker {


    StringRedisTemplate redisTemplate;

    public redisIdWorker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    // 全局唯一ID：正负号+时间戳(当前时间-起始时间)+序列号

    public long nextId(String prefixKey){


        //时间戳-timestamp(当前时间-起始时间)
        long currTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = currTime-BEIGIN_TIMESTAMP;

        //序列号-count
        // 业务表示+前缀+当前日期 精确到天
        String todayString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = redisTemplate.opsForValue().increment(UNIQUE_ID + prefixKey + ":" + todayString);


        //拼接 时间戳左移动32位+序列号
       return timestamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {


    }


}
