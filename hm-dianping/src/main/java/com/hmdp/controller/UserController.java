package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/test")
    public String test(){
        return "8082";
    }

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {

        Result res = userService.sendCode(phone,session);

        return res;
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) throws Exception {

        Result res = userService.login(loginForm,session);

        log.error("登录成功");

        return res;
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }


    //功能:传入博主id 去查询博主个人信息
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);

        if (user == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        return Result.ok(userDTO);
    }

    //功能: 签到功能
    //问题: 普通方式 将记录存入mysql 消耗内存的大
    //解决:  使用redis的bitmap存数据 开销非常小
    @PostMapping("/sign")
    public Result sign(){
        Long userId = UserHolder.getUser().getId();

        LocalDateTime now = LocalDateTime.now();

        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        //功能:
        // key: sign:1010:202302  val: dayOfMonth-1(0～29)  offset:1
        // val 越小 就在越左边
        // 10000000000000000000000100110000 表示 第1天 25天 28 29 签到过
        // 表示 当前用户在某月 的签到情况
        // 相当于30个槽位 签到了就标记为1 没签到就标记为0
        String key = USER_SIGN_KEY+userId+keySuffix;

        int dayOfMonth = now.getDayOfMonth();

        redisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);

        return Result.ok();
    }


    //功能: 统计用户 到当前为止 在本月的连续签到次数
    //问题:
    //解决:  使用redis的bitmap存数据 开销非常小
    @GetMapping ("/sign/count")
    public Result count(){
        Long userId = UserHolder.getUser().getId();

        LocalDateTime now = LocalDateTime.now();

        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        String key = USER_SIGN_KEY+userId+keySuffix;

        int dayOfMonth = now.getDayOfMonth();

        //获取全部签到记录
        List<Long> result = redisTemplate.opsForValue().bitField(key
                , BitFieldSubCommands
                        .create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));

        if(result==null||result.isEmpty()) return Result.ok(0);

        Long num = result.get(0);

        if(num==null||num==0) return Result.ok(0);

        int cnt= getCnt(num);

        return Result.ok(cnt);
    }


    // 给你一个十进制数字 得出其二进制数字的连续1的个数

    private static int getCnt(Long num) {

        int cnt=0;

        while (true){
            if((num &1)==0){
                break;
            }else{
                ++cnt;
            }
            num >>>= 1;
        }

        return cnt;
    }
}
