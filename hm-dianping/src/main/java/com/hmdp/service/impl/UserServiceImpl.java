package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();
        //1.验证手机号-正则表达式
        if (RegexUtils.isPhoneInvalid(phone)) {
            //非法
            return Result.fail("手机号格式错误!");
        }

        Object sessionCode = session.getAttribute("code");
        String code = loginForm.getCode();

        if (sessionCode==null||!sessionCode.toString().equals(code)) {
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

        session.setAttribute("user",userDTO);

        return Result.ok();
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
