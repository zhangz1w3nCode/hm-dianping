package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.tokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {

        //1.验证手机号-正则表达式
        if (RegexUtils.isPhoneInvalid(phone)) {
            //非法
            return Result.fail("手机号格式错误!");
        }

        //使用hutu工具类生成6位数的数字验证码
        String code = RandomUtil.randomNumbers(6);

        //FIXME -> session共享问题
        //session.setAttribute("code",code);

        //Todo -> 存到redis
        // key：业务表示符号+用户手机号  value：验证码code

        redisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送到用户手机
        log.error("发送验证码成功 验证码为:"+code);


        //返回成功状态吗
        return Result.ok();
    }


    // CAP理论：c一致性 a可用性 p分区融灾性
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) throws Exception {

        String phone = loginForm.getPhone();
        //1.验证手机号-正则表达式
        if (RegexUtils.isPhoneInvalid(phone)) {
            //非法
            return Result.fail("手机号格式错误!");
        }


        //FIXME -> session共享问题
        //Object sessionCode = session.getAttribute("code");

        //Todo ->  Reids去获取
        String sessionCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        if (sessionCode==null||!sessionCode.equals(code)) {
            //非法
            return Result.fail("验证码校验错误!");
        }

        User user = query().eq("phone", phone).one();

        if(user==null){
            //创建默认用户
            user = createUserByPhone(phone);
        }


        //只返回前端需要的数据 不全部返回
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);


        //FIXME -> session共享问题
        //session.setAttribute("user",userDTO);

        //Todo -> 存到redis 再返回token给前端口
        // key：业务标识符+JWT生成的token  value：user对象
        String token = tokenUtils.generateToken(phone);
        String objStr = JSON.toJSONString(userDTO);

        redisTemplate.opsForValue().set(LOGIN_USER_TOKEN+token,objStr);
        redisTemplate.expire(LOGIN_USER_TOKEN+token,TOKEN_USER_TTL,TimeUnit.MINUTES);

        return Result.ok(token);
    }


    //创建用户方法 入库方法
    private User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        //user.setNickName("user_"+RandomUtil.randomString(10));
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
