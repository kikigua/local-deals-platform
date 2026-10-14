package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    //extends ServiceImpl<UserMapper, User>用于配置Mybatis-Plus，实现CRUD操作

    @Resource
    private StringRedisTemplate stringRedisTemplate;//注入StringRedisTemplate

    /**
     * 发送验证码
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone))
            return Result.fail("手机号格式不正确");
        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //将验证码存入session
//        session.setAttribute("code", code);

        //将验证码存入redis：手机号->验证码(手机号作为key，验证码作为value)
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        // TODO: 发送验证码
        log.debug("发送验证码：" + code + "给：" + phone+"成功");
        //返回ok
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone()))
            return Result.fail("手机号格式不正确");
        //校验验证码，从redis中获取
        if (!loginForm.getCode().equals(stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone())))
            //不一致，报错
            return Result.fail("验证码不正确");
        //一致，根据手机号查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();//mybatis-plus实现查询
        //判断用户是否存在
        if (user == null) {
            //不存在，创建用户并保存
            user = createUserWithPhone(loginForm.getPhone());
        }

        //保存用户信息到session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //保存用户信息到redis：登录令牌->用户信息hash(登录令牌作为key，用户信息作为value)
        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString().replace("-", "");
        //将user对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //保存token和user信息
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //设置token的过期时间
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        //用户+手机号后4位作为昵称
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + phone.substring(7));
        //保存用户
        save(user);//mybatis-plus实现保存
        return user;
    }
}
