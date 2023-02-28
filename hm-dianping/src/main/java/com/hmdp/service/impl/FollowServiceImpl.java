package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_USER;

/**
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result followUser(Long blogUserId, Boolean isFollow) {

        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_USER+userId;
        if(isFollow){
            //关注用户
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(blogUserId);
            boolean isSuccess = save(follow);

            //redis存一份数据
            if(isSuccess){
                redisTemplate.opsForSet().add(key,blogUserId.toString());
            }

        }else {
            //取消关注用户
            QueryWrapper<Follow> wrapper = new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", blogUserId);
            boolean isSuccess = remove(wrapper);

            if(isSuccess){
                redisTemplate.opsForSet().remove(key,blogUserId.toString());
            }

        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long blogUserId) {
        Long userId = UserHolder.getUser().getId();

        Integer count =
                query().eq("user_id", userId)
                .eq("follow_user_id", blogUserId)
                .count();

        return Result.ok(count>0);
    }

    @Override
    public Result common(Long blogerId) {
        Long userId = UserHolder.getUser().getId();
        String userFollowKey = FOLLOW_USER+userId;
        String blogerFollowKey = FOLLOW_USER+blogerId;

        Set<String> intersect = redisTemplate.opsForSet().intersect(userFollowKey, blogerFollowKey);

        if(intersect==null||intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }

        List<Long> userIdList = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        List<User> userList = userService.listByIds(userIdList);

        List<UserDTO> userDTOList = userList.stream().map(user ->
                        BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOList);
    }
}
