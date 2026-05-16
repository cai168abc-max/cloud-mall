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
 * 物流轨迹实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("logistics_trace")
public class LogisticsTrace {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 物流信息ID
     */
    private Long logisticsId;
    
    /**
     * 轨迹时间
     */
    private LocalDateTime traceTime;
    
    /**
     * 轨迹状态
     */
    private String status;
    
    /**
     * 所在地点
     */
    private String location;
    
    /**
     * 轨迹描述
     */
    private String description;
    
    /**
     * 操作人/网点
     */
    private String operator;
    
    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
