package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FEED_USER;

/**
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IFollowService followService;


    //功能: 通过博客id 去找对应的博客的博主 再通过用户id得到用户信息 最后包装返回
    //问题:
    //解决:
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

    //功能:提取的一个方法:
    //    通过blog中的用户id 去用户表中查找 用户信息 存入blog的冗余字段中
    //问题:
    //解决:
    private void queryUserByBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    //功能: 分页查询当前的热帖子
    //问题:
    //解决:
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


    //功能:点赞博客 使用redis的zset集合
    //问题:
    //解决:
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
            update().setSql("liked = liked-1").eq("id", blogId).update();

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

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        Long blogUserId = user.getId();
        // 保存探店博文
        boolean isSuccess = save(blog);


        if(!isSuccess){
            return Result.fail("保存失败！");
        }

        //补丁
        // 发送给粉丝
        // 查找所有的粉丝集合
        List<Follow> fansList = followService.query().eq("follow_user_id", blogUserId)
                .list();

        // 推送到粉丝邮箱📮
        for(Follow fans:fansList){

            Long fansUserId = fans.getUserId();
            String key = FEED_USER+fansUserId.toString();

            //注意 存的是blog的在mysql中的id 而不是 博主id
            // 后续可以中redis中获取id 再去查找对应的博客
            String val = blog.getId().toString();
            long score = System.currentTimeMillis();

            redisTemplate.opsForZSet().add(key,val,score);

        }


        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {

        //当前用户id
        Long userId = UserHolder.getUser().getId();

        //收件箱
        String key = FEED_USER+userId.toString();

        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }

        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple:typedTuples) {
            // blogId
            String idStr = tuple.getValue();
            ids.add(Long.valueOf(idStr));


            // 时间戳
            long time = tuple.getScore().longValue();

            if(time==minTime){
                os++;
            }else{
                minTime = time;
                os=1;
            }

        }
        String idStr = StrUtil.join(",", ids);
        //根据blogIdList查询对应blog
        List<Blog> blogList = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        //注意
        for(Blog blog:blogList){
            //查询用户
            queryUserByBlog(blog);

            //还去redis中查询对应的点赞信息
            isBlogLiked(blog);
        }

        ScrollResult r = new ScrollResult();
        r.setList(blogList);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }
}
