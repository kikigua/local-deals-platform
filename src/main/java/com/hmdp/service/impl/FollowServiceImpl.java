package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 关注或取消关注用户
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("未登录");
        }
        String key = "follows:" + userId;//存入Redis的key
        if (isFollow) {
            //2. 执行关注：新增数据
            boolean save = save(new Follow().setUserId(userId).setFollowUserId(followUserId));
            if (save) {
                //3. 添加Redis数据
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            //4. 执行取关：删除数据
            boolean isRemove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isRemove) {
                //5. 删除Redis数据
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 判断当前用户是否关注了该用户
     */
    @Override
    public Result isFollow(Long followUserId) {
        //1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("未登录");
        }
        //2. 判断当前用户是否关注了该用户
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        //3. 返回结果
        return Result.ok(count > 0);
    }
}
