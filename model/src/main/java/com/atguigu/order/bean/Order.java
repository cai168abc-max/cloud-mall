package com.atguigu.order.bean;

import com.atguigu.common.enums.OrderStatus;
import com.atguigu.product.bean.Product;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("orders")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long merchantId;
    private String nickName;
    private String address;
    private BigDecimal totalPrice;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private Long couponId;

    @TableField("status")
    private OrderStatus status;

    @TableField("pay_time")
    private LocalDateTime payTime;

    @TableField("ship_time")
    private LocalDateTime shipTime;

    @TableField("complete_time")
    private LocalDateTime completeTime;

    /**
     * 是否已评价：0否 1是
     */
    @TableField("reviewed")
    private Integer reviewed;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<Product> productList;
}
