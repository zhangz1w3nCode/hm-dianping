package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {

        //不需要根据距离进行排序
        if(x==null||y==null){
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        //分页信息

        int from = (current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end  = current*SystemConstants.DEFAULT_PAGE_SIZE;

        String key =SHOP_GEO_KEY+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> res = redisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );

        if(res==null){
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = res.getContent();

        if(list.size()<=from){
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(list.size());

        Map<String,Distance> map = new HashMap<>(list.size());

        list.stream().skip(from).forEach(result->{

            String shopIdStr = result.getContent().getName();

            ids.add(Long.valueOf(shopIdStr));

            Distance distance = result.getDistance();

            map.put(shopIdStr,distance);

        });

        String idStr = StrUtil.join(",", ids);
        //根据blogIdList查询对应blog
        List<Shop> shopList = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for(Shop shop:shopList){
            shop.setDistance(map.get(shop.getId().toString()).getValue());
        }

        return Result.ok(shopList);
    }
}
