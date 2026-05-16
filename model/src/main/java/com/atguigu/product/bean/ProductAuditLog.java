package com.atguigu.product.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品审核日志实体类
 */
@Data
@TableName("product_audit_log")
public class ProductAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

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
     * 审核前状态
     */
    private Integer beforeStatus;

    /**
     * 审核后状态：1通过 0拒绝
     */
    private Integer afterStatus;

    /**
     * 审核原因/拒绝理由
     */
    private String reason;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
