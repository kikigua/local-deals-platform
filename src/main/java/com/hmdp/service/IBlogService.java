package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 根据id查询探店博文
     */
    Result queryBlogById(Long id);

    /**
     * 查询热门探店博文
     */
    Result queryHotBlog(Integer current);

    /**
     * 点赞操作（只能点一次，对每个blog）
     */
    Result likeBlog(Long id);
}
