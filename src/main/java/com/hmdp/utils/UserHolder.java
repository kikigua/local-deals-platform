package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

/**
 * 作用：用于保存当前登录用户，而当前登录用户是从redis中获取的用户信息。
 * @author 13507
 * @date 2023/5/24 14:58
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
