package com.hmdp;

import cn.hutool.core.lang.hash.Hash;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.redisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

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


    //补丁
    // 把商铺信息导入redis中
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private IShopService shopService;
    @Test
    void loadShopData(){

        List<Shop> shopList = shopService.list();
        Map<Long,List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        for(Map.Entry<Long,List<Shop>> entry:map.entrySet()){
            Long typeId = entry.getKey();
            String key =SHOP_GEO_KEY+typeId;
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());

            for(Shop s:value){
                //redisTemplate.opsForGeo().add(key,new Point(s.getX(),s.getY()),s.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        s.getId().toString(),
                        new Point(s.getX(),s.getY())
                ));
            }

            redisTemplate.opsForGeo().add(key,locations);
        }



    }


}
