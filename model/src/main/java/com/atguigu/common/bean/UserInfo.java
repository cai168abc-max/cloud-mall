package com.atguigu.common.bean;

import com.atguigu.common.enums.UserRole;
import lombok.Data;

@Data
public class UserInfo {
    private Long id;
    private String nickName;
    private UserRole role;
    private String phone;
    private String email;
    private String avatar;
    private String gender;
    private Integer age;
    private String birthday;
    private String bio;
    private String merchantName;
    private Boolean verified;
    private Boolean enabled;
}
