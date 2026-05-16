package com.atguigu.order.bean;

import com.atguigu.common.enums.LogisticsStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物流信息实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("logistics_info")
public class LogisticsInfo {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 订单ID，唯一关联
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
     * 物流单号
     */
    private String trackingNo;
    
    /**
     * 物流公司
     */
    private String carrier;
    
    /**
     * 物流状态
     */
    @TableField("status")
    private LogisticsStatus status;
    
    /**
     * 发件人姓名
     */
    private String senderName;
    
    /**
     * 发件人电话
     */
    private String senderPhone;
    
    /**
     * 发件人地址
     */
    private String senderAddress;
    
    /**
     * 收件人姓名
     */
    private String receiverName;
    
    /**
     * 收件人电话
     */
    private String receiverPhone;
    
    /**
     * 收件人地址
     */
    private String receiverAddress;
    
    /**
     * 预计到达时间
     */
    private LocalDateTime estimatedArrivalTime;
    
    /**
     * 实际到达时间
     */
    private LocalDateTime actualArrivalTime;
    
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
    
    /**
     * 物流轨迹列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<LogisticsTrace> traceList;
}
