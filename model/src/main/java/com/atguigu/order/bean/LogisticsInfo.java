package com.atguigu.order.bean;

import com.atguigu.common.enums.LogisticsStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 物流信息实体类
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
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
    
    public LogisticsInfo(Long id, Long orderId, Long userId, Long merchantId, String trackingNo,
                         String carrier, LogisticsStatus status, String senderName, String senderPhone,
                         String senderAddress, String receiverName, String receiverPhone,
                         String receiverAddress, LocalDateTime estimatedArrivalTime,
                         LocalDateTime actualArrivalTime, LocalDateTime createTime,
                         LocalDateTime updateTime, List<LogisticsTrace> traceList) {
        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.merchantId = merchantId;
        this.trackingNo = trackingNo;
        this.carrier = carrier;
        this.status = status;
        this.senderName = senderName;
        this.senderPhone = senderPhone;
        this.senderAddress = senderAddress;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.receiverAddress = receiverAddress;
        this.estimatedArrivalTime = estimatedArrivalTime;
        this.actualArrivalTime = actualArrivalTime;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.traceList = traceList != null ? new ArrayList<>(traceList) : null;
    }
    
    public List<LogisticsTrace> getTraceList() {
        return traceList != null ? Collections.unmodifiableList(traceList) : null;
    }
    
    public void setTraceList(final List<LogisticsTrace> traceList) {
        this.traceList = traceList != null ? new ArrayList<>(traceList) : null;
    }
    
    public static LogisticsInfoBuilder builder() {
        return new LogisticsInfoBuilder();
    }
    
    public static class LogisticsInfoBuilder {
        private Long id;
        private Long orderId;
        private Long userId;
        private Long merchantId;
        private String trackingNo;
        private String carrier;
        private LogisticsStatus status;
        private String senderName;
        private String senderPhone;
        private String senderAddress;
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        private LocalDateTime estimatedArrivalTime;
        private LocalDateTime actualArrivalTime;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private List<LogisticsTrace> traceList;
        
        public LogisticsInfoBuilder id(Long id) {
            this.id = id;
            return this;
        }
        
        public LogisticsInfoBuilder orderId(Long orderId) {
            this.orderId = orderId;
            return this;
        }
        
        public LogisticsInfoBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }
        
        public LogisticsInfoBuilder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }
        
        public LogisticsInfoBuilder trackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
            return this;
        }
        
        public LogisticsInfoBuilder carrier(String carrier) {
            this.carrier = carrier;
            return this;
        }
        
        public LogisticsInfoBuilder status(LogisticsStatus status) {
            this.status = status;
            return this;
        }
        
        public LogisticsInfoBuilder senderName(String senderName) {
            this.senderName = senderName;
            return this;
        }
        
        public LogisticsInfoBuilder senderPhone(String senderPhone) {
            this.senderPhone = senderPhone;
            return this;
        }
        
        public LogisticsInfoBuilder senderAddress(String senderAddress) {
            this.senderAddress = senderAddress;
            return this;
        }
        
        public LogisticsInfoBuilder receiverName(String receiverName) {
            this.receiverName = receiverName;
            return this;
        }
        
        public LogisticsInfoBuilder receiverPhone(String receiverPhone) {
            this.receiverPhone = receiverPhone;
            return this;
        }
        
        public LogisticsInfoBuilder receiverAddress(String receiverAddress) {
            this.receiverAddress = receiverAddress;
            return this;
        }
        
        public LogisticsInfoBuilder estimatedArrivalTime(LocalDateTime estimatedArrivalTime) {
            this.estimatedArrivalTime = estimatedArrivalTime;
            return this;
        }
        
        public LogisticsInfoBuilder actualArrivalTime(LocalDateTime actualArrivalTime) {
            this.actualArrivalTime = actualArrivalTime;
            return this;
        }
        
        public LogisticsInfoBuilder createTime(LocalDateTime createTime) {
            this.createTime = createTime;
            return this;
        }
        
        public LogisticsInfoBuilder updateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
            return this;
        }
        
        public LogisticsInfoBuilder traceList(List<LogisticsTrace> traceList) {
            this.traceList = traceList != null ? new ArrayList<>(traceList) : null;
            return this;
        }
        
        public LogisticsInfo build() {
            return new LogisticsInfo(id, orderId, userId, merchantId, trackingNo, carrier,
                    status, senderName, senderPhone, senderAddress, receiverName,
                    receiverPhone, receiverAddress, estimatedArrivalTime, actualArrivalTime,
                    createTime, updateTime, this.traceList);
        }
    }
}
