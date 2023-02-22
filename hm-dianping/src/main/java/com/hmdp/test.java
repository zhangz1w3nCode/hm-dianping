package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.redisIdWorker;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class test {

    @Resource
    private ShopServiceImpl service;

    @Resource
    private com.hmdp.utils.redisIdWorker redisIdWorker;

    private ExecutorService pool = Executors.newFixedThreadPool(500);

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

    public static void main(String[] args) throws InterruptedException {


        new test().tRedisIdWorker();

        System.out.println();

    }
}
