package com.atguigu.order.mapper;

import com.atguigu.order.bean.AfterSaleTicket;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 售后工单Mapper
 */
@Mapper
public interface AfterSaleTicketMapper extends BaseMapper<AfterSaleTicket> {

    /**
     * 根据订单ID查询售后工单
     * @param orderId 订单ID
     * @return 售后工单
     */
    @Select("SELECT * FROM after_sale_ticket WHERE order_id = #{orderId} ORDER BY create_time DESC LIMIT 1")
    AfterSaleTicket selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据订单ID查询进行中的售后工单
     * @param orderId 订单ID
     * @return 售后工单列表
     */
    @Select("SELECT * FROM after_sale_ticket WHERE order_id = #{orderId} AND status IN ('PENDING', 'PROCESSING', 'APPROVED') ORDER BY create_time DESC")
    List<AfterSaleTicket> selectProcessingByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据用户ID查询售后工单列表
     * @param userId 用户ID
     * @return 售后工单列表
     */
    @Select("SELECT * FROM after_sale_ticket WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<AfterSaleTicket> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据商家ID查询售后工单列表
     * @param merchantId 商家ID
     * @return 售后工单列表
     */
    @Select("SELECT * FROM after_sale_ticket WHERE merchant_id = #{merchantId} ORDER BY create_time DESC")
    List<AfterSaleTicket> selectByMerchantId(@Param("merchantId") Long merchantId);

    /**
     * 根据商家ID和状态查询售后工单列表
     * @param merchantId 商家ID
     * @param status 状态
     * @return 售后工单列表
     */
    @Select("SELECT * FROM after_sale_ticket WHERE merchant_id = #{merchantId} AND status = #{status} ORDER BY create_time DESC")
    List<AfterSaleTicket> selectByMerchantIdAndStatus(@Param("merchantId") Long merchantId, @Param("status") String status);

    /**
     * 更新工单状态
     * @param id 工单ID
     * @param status 状态
     * @return 更新记录数
     */
    @Update("UPDATE after_sale_ticket SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 更新工单状态为已同意
     * @param id 工单ID
     * @param status 状态
     * @param refundAmount 退款金额
     * @return 更新记录数
     */
    @Update("UPDATE after_sale_ticket SET status = #{status}, refund_amount = #{refundAmount}, update_time = NOW() WHERE id = #{id}")
    int updateStatusToApproved(@Param("id") Long id, @Param("status") String status, @Param("refundAmount") java.math.BigDecimal refundAmount);

    /**
     * 更新工单状态为已拒绝
     * @param id 工单ID
     * @param status 状态
     * @param rejectReason 拒绝原因
     * @return 更新记录数
     */
    @Update("UPDATE after_sale_ticket SET status = #{status}, reject_reason = #{rejectReason}, update_time = NOW() WHERE id = #{id}")
    int updateStatusToRejected(@Param("id") Long id, @Param("status") String status, @Param("rejectReason") String rejectReason);

    /**
     * 更新工单状态为已完成（包含退款流水号）
     * @param id 工单ID
     * @param status 状态
     * @param transactionNo 退款交易流水号
     * @return 更新记录数
     */
    @Update("UPDATE after_sale_ticket SET status = #{status}, refund_transaction_no = #{transactionNo}, update_time = NOW() WHERE id = #{id}")
    int updateStatusToCompleted(@Param("id") Long id, @Param("status") String status, @Param("transactionNo") String transactionNo);

    /**
     * 检查订单是否有进行中的售后工单
     * @param orderId 订单ID
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM after_sale_ticket WHERE order_id = #{orderId} AND status IN ('PENDING', 'PROCESSING', 'APPROVED')")
    int countProcessingByOrderId(@Param("orderId") Long orderId);

    /**
     * 插入售后工单
     * @param ticket 售后工单
     * @return 插入记录数
     */
    @Insert("INSERT INTO after_sale_ticket (order_id, user_id, merchant_id, type, reason, description, images, status, create_time, update_time) "
            + "VALUES (#{orderId}, #{userId}, #{merchantId}, #{type}, #{reason}, #{description}, #{images}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertTicket(AfterSaleTicket ticket);
}
