package com.atguigu.product.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存预警记录实体类
 */
@Data
@TableName("inventory_alert_log")
public class InventoryAlertLog {

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
     * 当前库存
     */
    private Integer stock;

    /**
     * 预警阈值
     */
    private Integer threshold;

    /**
     * 状态：PENDING/SENT/FAILED
     */
    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;
}
