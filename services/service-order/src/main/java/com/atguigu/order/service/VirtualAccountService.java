package com.atguigu.order.service;

import com.atguigu.order.bean.VirtualAccount;
import com.atguigu.order.bean.VirtualAccountLog;

import java.math.BigDecimal;
import java.util.List;

/**
 * 虚拟账户服务接口
 */
public interface VirtualAccountService {

    /**
     * 获取或创建用户账户
     * @param userId 用户ID
     * @return 虚拟账户
     */
    VirtualAccount getOrCreateAccount(Long userId);

    /**
     * 充值
     * @param userId 用户ID
     * @param amount 充值金额
     * @param transactionNo 交易流水号（可选，用于幂等性）
     * @return 流水记录
     */
    VirtualAccountLog recharge(Long userId, BigDecimal amount, String transactionNo);

    /**
     * 查询余额
     * @param userId 用户ID
     * @return 账户余额
     */
    BigDecimal getBalance(Long userId);

    /**
     * 查询账户信息
     * @param userId 用户ID
     * @return 虚拟账户
     */
    VirtualAccount getAccount(Long userId);

    /**
     * 支付（扣款）
     * @param userId 用户ID
     * @param amount 支付金额
     * @param orderId 订单ID
     * @param transactionNo 交易流水号（可选，用于幂等性）
     * @return 流水记录
     */
    VirtualAccountLog pay(Long userId, BigDecimal amount, Long orderId, String transactionNo);

    /**
     * 退款
     * @param userId 用户ID
     * @param amount 退款金额
     * @param orderId 订单ID
     * @param transactionNo 交易流水号（可选，用于幂等性）
     * @return 流水记录
     */
    VirtualAccountLog refund(Long userId, BigDecimal amount, Long orderId, String transactionNo);

    /**
     * 冻结金额
     * @param userId 用户ID
     * @param amount 冻结金额
     * @return 是否成功
     */
    boolean freeze(Long userId, BigDecimal amount);

    /**
     * 解冻金额
     * @param userId 用户ID
     * @param amount 解冻金额
     * @return 是否成功
     */
    boolean unfreeze(Long userId, BigDecimal amount);

    /**
     * 查询交易流水
     * @param userId 用户ID
     * @return 流水记录列表
     */
    List<VirtualAccountLog> getTransactionLogs(Long userId);

    /**
     * 根据订单ID查询流水
     * @param orderId 订单ID
     * @return 流水记录
     */
    VirtualAccountLog getLogByOrderId(Long orderId);

    /**
     * 刷新余额缓存
     * @param userId 用户ID
     */
    void refreshBalanceCache(Long userId);
}
