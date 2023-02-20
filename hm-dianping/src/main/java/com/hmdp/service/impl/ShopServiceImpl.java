package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;


    //获取互斥锁-缓存击穿
    public boolean tryLock(String key){
        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(key, "1",LOCK_SHOP_TTL,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    //释放互斥锁-缓存击穿
    public void delLock(String key){
       redisTemplate.delete(key);
    }

    @Override
    public Result queryById(Long id) {

        //缓存穿透
        //Shop shop = cacheCrossover(id);

        //缓存击穿
        Shop shop1 = cacheThrough(id);

        if(shop1==null){
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop1);
    }

    //缓存穿透
    public Shop cacheCrossover(Long id){
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // 是"" 也会返回false
        //缓存空值 防止缓存穿透
        if(shopJson!=null){
            return null;
        }

        Shop shop = getById(id);


        if(shop==null) {

            //缓存空值 防止缓存穿透
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }


        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }


    //缓存击穿案例
    public Shop cacheThrough(Long id){
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }


        if(shopJson!=null){

            return null;
        }

        //缓存击穿
        // 获取互斥锁

        Shop shop=null;

        try {

            boolean isLock = tryLock(LOCK_SHOP_KEY+id);

            if(!isLock){
                // 没拿到锁 重试 -递归方法
                Thread.sleep(50);

                return cacheThrough(id);
            }

            //拿到锁了 去查数据库 并且 存入缓存 释放锁

            shop = getById(id);
            log.error("查询数据库");

            Thread.sleep(200);

            if(shop==null) {

                //缓存空值 防止缓存穿透
                redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }


            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            //释放锁
            delLock(LOCK_SHOP_KEY+id);
        }


        return shop;
    }





    //更新商店方法--在有缓存的情况
    //更新数据库 再 删除缓存
    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();

        if(id==null) return  Result.fail("商店不存在!");

        updateById(shop);

        redisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
