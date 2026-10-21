package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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
        if (isFollow) {
            //2. 执行关注：新增数据
            save(new Follow().setUserId(userId).setFollowUserId(followUserId));
        } else {
            //3. 执行取关：删除数据
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
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
