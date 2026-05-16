package com.atguigu.order.bean;

import com.atguigu.common.enums.CouponStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class Coupon {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Long userId;
    private String name;
    private BigDecimal amount;
    private BigDecimal threshold;
    private Integer stock;

    @TableField("status")
    private CouponStatus status;

    @TableField("valid_from")
    private LocalDateTime validFrom;

    @TableField("valid_to")
    private LocalDateTime validTo;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
