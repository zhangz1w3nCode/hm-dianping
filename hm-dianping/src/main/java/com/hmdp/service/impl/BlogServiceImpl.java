package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog==null) return Result.fail("笔记不存在！");

        //查询用户
        queryUserByBlog(blog);

        //还去redis中查询对应的点赞信息
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    //功能: 获取点赞过 笔记的人 查询用户是否点赞 并且设置到blog中
    //问题: 用户没有登录报空指针bug
    //解决: 没有登录就直接结束方法
    private void isBlogLiked(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO==null){
            return;
        }

        Long userId = UserHolder.getUser().getId();
        Long blogId = blog.getId();

        String key = RedisConstants.BLOG_LIKED_KEY+blogId;
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());

        blog.setIsLike(score!=null);
    }


    //通过blog中的用户id 去用户表中查找 用户信息 存入blog的冗余字段中
    private void queryUserByBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.isBlogLiked(blog);
            this.queryUserByBlog(blog);
        });

        return Result.ok(records);
    }


    //功能:点赞方法
    @Override
    public Object likeBlog(Long blogId) {

        Long userId = UserHolder.getUser().getId();


        //存入redis  key = BLOG_LIKED_KEY+blogId  value = userId
        String key = RedisConstants.BLOG_LIKED_KEY+blogId;
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());

        if(score==null){
            //没被点赞
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", blogId).update();

            //存入redis zset
            if(isSuccess){
                // zadd key value score
                // 分数是时间戳 按时间戳排序
                redisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }


        }else{
            boolean isSuccess = update().setSql("liked = liked-1").eq("id", blogId).update();

            redisTemplate.opsForZSet().remove(key,userId.toString());

        }

        return Result.ok();
    }


    //功能: 获取点赞过 笔记的人
    //问题:
    //解决:
    @Override
    public Object queryBlogLikes(Long id) {

        String key = RedisConstants.BLOG_LIKED_KEY+id;
        Set<String> top5Set = redisTemplate.opsForZSet().range(key, 0, 4);

        if(top5Set==null||top5Set.isEmpty()){
            return Result.ok(Collections.emptyList());
        }

        List<Long> userIdList = top5Set.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> userList = userService.listByIds(userIdList);
        List<UserDTO> userDTOList = userList
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOList);
    }
}
