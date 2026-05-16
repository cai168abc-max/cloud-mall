package com.atguigu.common.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存预警消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAlertMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 预警记录ID
     */
    private Long alertLogId;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 商家ID
     */
    private Long merchantId;

    /**
     * 当前库存
     */
    private Integer stock;

    /**
     * 预警阈值
     */
    private Integer threshold;

    /**
     * 消息时间戳
     */
    private Long timestamp;
}
