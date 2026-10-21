package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注或取消关注用户
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断当前用户是否关注了该用户
     */
    Result isFollow(Long followUserId);

    /**
     * 根据用户id查询共同关注的用户列表
     */
    Result followCommons(Long id);
}
