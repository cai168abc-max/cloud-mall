package com.atguigu.common.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品审核结果消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAuditMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 商家ID
     */
    private Long merchantId;

    /**
     * 审核人ID
     */
    private Long auditorId;

    /**
     * 审核结果：true-通过，false-拒绝
     */
    private Boolean approved;

    /**
     * 审核原因/拒绝理由
     */
    private String reason;

    /**
     * 消息类型：SINGLE-单个审核，BATCH-批量审核
     */
    private String type;

    /**
     * 消息时间戳
     */
    private Long timestamp;
}
