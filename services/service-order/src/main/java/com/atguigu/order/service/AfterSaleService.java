package com.atguigu.order.service;

import com.atguigu.order.bean.AfterSaleTicket;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;
import java.util.List;

/**
 * 售后服务接口
 */
public interface AfterSaleService {

    /**
     * 申请售后
     * @param orderId 订单ID
     * @param type 售后类型：REFUND-仅退款/RETURN-退货退款/EXCHANGE-换货
     * @param reason 申请原因
     * @param description 详细描述
     * @param images 凭证图片（逗号分隔）
     * @param userId 用户ID
     * @return 售后工单
     */
    AfterSaleTicket applyAfterSale(Long orderId, String type, String reason, String description, String images, Long userId);

    /**
     * 商家同意售后
     * @param ticketId 工单ID
     * @param merchantId 商家ID
     * @param refundAmount 退款金额（可选，为空则使用订单支付金额）
     * @return 是否成功
     */
    boolean approveAfterSale(Long ticketId, Long merchantId, BigDecimal refundAmount);

    /**
     * 商家拒绝售后
     * @param ticketId 工单ID
     * @param merchantId 商家ID
     * @param rejectReason 拒绝原因
     * @return 是否成功
     */
    boolean rejectAfterSale(Long ticketId, Long merchantId, String rejectReason);

    /**
     * 手动退款（兜底方案）
     * @param ticketId 工单ID
     * @param merchantId 商家ID
     * @return 是否成功
     */
    boolean manualRefund(Long ticketId, Long merchantId);

    /**
     * 用户取消售后
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean cancelAfterSale(Long ticketId, Long userId);

    /**
     * 查询售后工单详情
     * @param ticketId 工单ID
     * @return 售后工单
     */
    AfterSaleTicket getTicketById(Long ticketId);

    /**
     * 根据订单ID查询售后工单
     * @param orderId 订单ID
     * @return 售后工单
     */
    AfterSaleTicket getTicketByOrderId(Long orderId);

    /**
     * 查询用户的售后工单列表
     * @param userId 用户ID
     * @return 售后工单列表
     */
    List<AfterSaleTicket> listTicketsByUserId(Long userId);

    /**
     * 分页查询用户的售后工单列表
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 分页售后工单列表
     */
    IPage<AfterSaleTicket> listTicketsByUserId(Long userId, int pageNum, int pageSize);

    /**
     * 查询商家的售后工单列表
     * @param merchantId 商家ID
     * @return 售后工单列表
     */
    List<AfterSaleTicket> listTicketsByMerchantId(Long merchantId);

    /**
     * 分页查询商家的售后工单列表
     * @param merchantId 商家ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 分页售后工单列表
     */
    IPage<AfterSaleTicket> listTicketsByMerchantId(Long merchantId, int pageNum, int pageSize);

    /**
     * 根据商家ID和状态查询售后工单列表
     * @param merchantId 商家ID
     * @param status 状态
     * @return 售后工单列表
     */
    List<AfterSaleTicket> listTicketsByMerchantIdAndStatus(Long merchantId, String status);
}
