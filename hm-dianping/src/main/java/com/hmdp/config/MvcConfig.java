package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.refreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/shop/**",
                "/shop-type/**",
                "/voucher/**",
                "/upload/**",
                "/blog/hot",
                "/user/code",
                "/user/login"
        ).order(1);


        //刷新token的拦截器
        registry.addInterceptor(new refreshTokenInterceptor(redisTemplate)).addPathPatterns(
                "/**"
        ).order(0);
    }
}
