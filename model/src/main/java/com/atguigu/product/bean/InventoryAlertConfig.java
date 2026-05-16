package com.atguigu.product.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存预警配置实体类
 */
@Data
@TableName("inventory_alert_config")
public class InventoryAlertConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 预警阈值
     */
    private Integer threshold;

    /**
     * 预警间隔（分钟）
     */
    private Integer alertInterval;

    /**
     * 状态：1启用 0禁用
     */
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
