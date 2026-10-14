package com.hmdp.dto;

import lombok.Data;

@Data
public class UserDTO {//用户信息隐藏，防止被恶意修改。这里创建DTO对象，只包含前端登录需要的用户信息。
    private Long id;
    private String nickName;
    private String icon;
}
