package com.atguigu.common.bean;

import com.atguigu.common.enums.UserRole;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class UserAccount {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;
    private String email;
    private String salt;
    private String passwordHash;
    private UserRole role;
    private String nickName;
    private String merchantName;
    private String avatarUrl;
    private Boolean enabled = true;
    private Boolean verified = false;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
