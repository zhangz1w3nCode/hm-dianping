package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.StringUtil;
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


    @Override
    public Result queryById(Long id) {

        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // 是"" 也会返回false
        if(shopJson!=null){
            return Result.fail("店铺不存在Redis!");
        }

        Shop shop = getById(id);


        if(shop==null) {

            //缓存空值 防止缓存穿透
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);

            return Result.fail("店铺不存在数据库!");
        }


        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shop);
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
