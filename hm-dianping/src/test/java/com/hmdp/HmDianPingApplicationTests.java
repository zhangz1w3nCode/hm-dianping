package com.hmdp;

import com.hmdp.utils.redisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private redisIdWorker redisIdWorker;

    private ExecutorService pool = Executors.newFixedThreadPool(500);

    @Test
    public void tRedisIdWorker() throws InterruptedException {

        CountDownLatch count = new CountDownLatch(300);

        Runnable task =()->{
            for(int i=0;i<100;++i){
                long id = redisIdWorker.nextId("order");
                System.out.println("id:"+id);
            }
            count.countDown();
        };
        long start = System.currentTimeMillis();

        for(int i=0;i<300;++i){
            pool.submit(task);
        }

        count.await();

        long end = System.currentTimeMillis();

        System.out.println("耗时："+(end -start));
    }


}
