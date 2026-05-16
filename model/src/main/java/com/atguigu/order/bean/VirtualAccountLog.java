package com.atguigu.order.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 虚拟账户交易流水实体类
 * 记录所有账户变动明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("virtual_account_log")
public class VirtualAccountLog {

    /**
     * 流水ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 交易流水号（唯一）
     */
    private String transactionNo;

    /**
     * 账户ID
     */
    private Long accountId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 交易类型：RECHARGE/PAY/REFUND/FREEZE/UNFREEZE
     */
    private String type;

    /**
     * 交易金额
     */
    private BigDecimal amount;

    /**
     * 变更前余额
     */
    private BigDecimal balanceBefore;

    /**
     * 变更后余额
     */
    private BigDecimal balanceAfter;

    /**
     * 关联订单ID
     */
    private Long relatedOrderId;

    /**
     * 交易状态：SUCCESS/FAILED/PENDING
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 交易类型常量
     */
    public static final String TYPE_RECHARGE = "RECHARGE";    // 充值
    public static final String TYPE_PAY = "PAY";              // 支付
    public static final String TYPE_REFUND = "REFUND";        // 退款
    public static final String TYPE_FREEZE = "FREEZE";        // 冻结
    public static final String TYPE_UNFREEZE = "UNFREEZE";    // 解冻

    /**
     * 交易状态常量
     */
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PENDING = "PENDING";
}
