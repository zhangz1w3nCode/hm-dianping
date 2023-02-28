package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


//接口：关注用户接口
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    //功能: 用户关注博主功能
    //问题:
    //解决:
    @PutMapping("/{id}/{isFollow}")
    public Result followUser(@PathVariable("id") Long id,@PathVariable("isFollow") Boolean isFollow){
        return followService.followUser(id,isFollow);
    }

    //功能: 判断用户是否关注博主功能
    //问题:
    //解决:

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id){
        return followService.isFollow(id);
    }


    //功能: 共同关注
    //问题: 普通方式去db中查找对应关注集合列表 再取交集太耗性能
    //解决: 使用redis的set集合取交集 速度非常快
    @GetMapping("/common/{id}")
    public Result common(@PathVariable("id") Long blogerId){
        return followService.common(blogerId);
    }


}
