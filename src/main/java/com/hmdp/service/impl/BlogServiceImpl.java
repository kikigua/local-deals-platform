package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询热门探店博文
     */
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
            this.queryBlogUser(blog);
            //查询blog用户是否点赞
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询博客用户。内置静态方法
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 根据id查询探店博文
     */
    @Override
    public Result queryBlogById(Long id) {
        // 根据id查询探店博文
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        // 查询用户
        queryBlogUser(blog);
        //查询blog用户是否点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //1、获取登录用户id
        Long userId = UserHolder.getUser().getId();
        //2、判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    /**
     * 点赞操作（只能点一次，对每个blog）
     */
    @Override
    public Result likeBlog(Long id) {
        //1、获取登录用户id
        Long userId = UserHolder.getUser().getId();
        //2、判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //3、如果未点赞，则点赞
            //3.1、数据库点赞数量+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2、保存用户到redis的set集合
            if (isSuccess) {
                //解释一下ZSet，ZSet是Redis中的一个数据结构，类似于Set，但每个元素关联一个分数（时间戳），可以用于排序
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else {
            //4、如果已点赞，则取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //4.1、数据库点赞数量-1
            if (isSuccess) {
                //4.2、从redis的set集合中删除用户
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        //5、返回结果
        return Result.ok();
    }

}
