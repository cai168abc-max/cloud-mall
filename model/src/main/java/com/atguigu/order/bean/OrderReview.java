package com.atguigu.order.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单评价实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_review")
public class OrderReview {

    /**
     * 评价ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID，唯一约束防止重复评价
     */
    private Long orderId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商家ID
     */
    private Long merchantId;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 评分：1-5星
     */
    private Integer rating;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 评价图片URL列表，JSON数组格式
     */
    private String images;

    /**
     * 是否匿名评价：0否 1是
     */
    private Integer anonymous;

    /**
     * 状态：1正常 0隐藏
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
}
