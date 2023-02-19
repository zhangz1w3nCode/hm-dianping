package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.hmdp.dto.UserDTO;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TOKEN;
import static com.hmdp.utils.RedisConstants.TOKEN_USER_TTL;

//登录拦截器

public class refreshTokenInterceptor implements HandlerInterceptor {


    //
    private StringRedisTemplate redisTemplate;

    public refreshTokenInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {


        // Todo：redis+jwt优化
        String token = request.getHeader("authorization");
        
        if(StrUtil.isBlank(token)){
            return  true;
        }

        String objStr = redisTemplate.opsForValue().get(LOGIN_USER_TOKEN + token);

        if(StringUtil.isNullOrEmpty(objStr)){
            return  true;
        }

        UserDTO userDTO = JSON.parseObject(objStr, UserDTO.class);

        //存入thread-local
        UserHolder.saveUser(userDTO);

        //FIXME:刷新 token有效时间
        redisTemplate.expire(LOGIN_USER_TOKEN+token,TOKEN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();//移除用户 避免内存泄漏
    }
}
