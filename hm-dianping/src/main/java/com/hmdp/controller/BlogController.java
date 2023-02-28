package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

//接口: 发布博文-笔记
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;


    //功能: 保存一个博客到数据库
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {

        // 返回id
        return blogService.saveBlog(blog);
    }

    //功能: 点赞功能
    //问题: 接口防刷
    //解决: 判断用户是否赞过 -redis存储用户是否点赞和点赞数量
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量

        return Result.ok(blogService.likeBlog(id));
    }

    //功能: 获取点赞过 笔记的人
    //问题: 接口防刷
    //解决: 判断用户是否赞过 -redis存储用户是否点赞和点赞数量
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        // 修改点赞数量

        return Result.ok(blogService.queryBlogLikes(id));
    }


    //功能：根据博客id 去查找博主发布的笔记
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long blogId) {

        return blogService.queryBlogById(blogId);
    }


    //功能：查找我发布的笔记
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }


    //功能：根据博主id 去查找博主发布的笔记
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long userId
            ) {
        // 根据用户ID查询
        Page<Blog> page = blogService.query()
                .eq("user_id", userId).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    //功能：根据like数 做一个排行榜
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        return blogService.queryHotBlog(current);
    }


    //功能: 滚动分页查询实现推送流
    //问题: 普通方式去db中查找对应关注集合列表 再取交集太耗性能
    //解决: 使用redis的set集合取交集 速度非常快
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max
            ,@RequestParam(value = "offset",defaultValue = "0") Integer offset
    ){

        return blogService.queryBlogOfFollow(max,offset);
    }
}
