package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {

        //1.验证手机号-正则表达式
        if (RegexUtils.isPhoneInvalid(phone)) {
            //非法
            return Result.fail("手机号格式错误!");
        }

        //使用hutu工具类生成6位数的数字验证码
        String code = RandomUtil.randomNumbers(6);

        //存入session
        session.setAttribute("code",code);

        //发送到用户手机
        log.error("发送验证码成功 验证码为:"+code);


        //返回成功状态吗
        return Result.ok();
    }
}
