package com.atguigu.order.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后工单实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("after_sale_ticket")
public class AfterSaleTicket {

    /**
     * 售后类型：仅退款
     */
    public static final String TYPE_REFUND = "REFUND";

    /**
     * 售后类型：退货退款
     */
    public static final String TYPE_RETURN = "RETURN";

    /**
     * 售后类型：换货
     */
    public static final String TYPE_EXCHANGE = "EXCHANGE";

    /**
     * 工单状态：待处理
     */
    public static final String STATUS_PENDING = "PENDING";

    /**
     * 工单状态：已同意
     */
    public static final String STATUS_APPROVED = "APPROVED";

    /**
     * 工单状态：已拒绝
     */
    public static final String STATUS_REJECTED = "REJECTED";

    /**
     * 工单状态：处理中
     */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /**
     * 工单状态：已完成
     */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /**
     * 工单状态：已取消
     */
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
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
     * 类型：REFUND-仅退款/RETURN-退货退款/EXCHANGE-换货
     */
    private String type;

    /**
     * 申请原因
     */
    private String reason;

    /**
     * 详细描述
     */
    private String description;

    /**
     * 凭证图片，逗号分隔
     */
    private String images;

    /**
     * 状态：PENDING-待处理/APPROVED-已同意/REJECTED-已拒绝/PROCESSING-处理中/COMPLETED-已完成/CANCELLED-已取消
     */
    private String status;

    /**
     * 拒绝原因
     */
    private String rejectReason;

    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    /**
     * 退款交易流水号
     */
    private String refundTransactionNo;

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
