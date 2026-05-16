package com.atguigu.order.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 虚拟账户实体类
 * 用于管理用户的虚拟账户余额
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("virtual_account")
public class VirtualAccount {

    /**
     * 账户ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（唯一）
     */
    private Long userId;

    /**
     * 账户余额
     */
    private BigDecimal balance;

    /**
     * 冻结金额
     */
    private BigDecimal frozenAmount;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    /**
     * 账户状态：1正常 0冻结
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 账户状态常量
     */
    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_FROZEN = 0;
}
