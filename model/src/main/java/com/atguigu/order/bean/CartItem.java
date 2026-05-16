package com.atguigu.order.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cart_item")
public class CartItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cartId;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private Boolean checked;
    private Long categoryId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
