package com.atguigu.common.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评价消息
 * 用于异步更新商品评价统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评价ID
     */
    private Long reviewId;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商家ID
     */
    private Long merchantId;

    /**
     * 评分
     */
    private Integer rating;

    /**
     * 操作类型：CREATE-创建评价, UPDATE-更新评价, DELETE-删除评价
     */
    private String action;

    /**
     * 消息时间戳
     */
    private Long timestamp;

    /**
     * 操作类型常量
     */
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
}
