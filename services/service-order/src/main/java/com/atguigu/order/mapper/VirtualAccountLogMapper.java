package com.atguigu.order.mapper;

import com.atguigu.order.bean.VirtualAccountLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 虚拟账户流水Mapper接口
 */
@Mapper
public interface VirtualAccountLogMapper extends BaseMapper<VirtualAccountLog> {

    /**
     * 根据流水号查询流水记录
     * @param transactionNo 交易流水号
     * @return 流水记录
     */
    @Select("SELECT * FROM virtual_account_log WHERE transaction_no = #{transactionNo}")
    VirtualAccountLog selectByTransactionNo(@Param("transactionNo") String transactionNo);

    /**
     * 根据账户ID查询流水记录
     * @param accountId 账户ID
     * @return 流水记录列表
     */
    @Select("SELECT * FROM virtual_account_log WHERE account_id = #{accountId} ORDER BY create_time DESC LIMIT 100")
    List<VirtualAccountLog> selectByAccountId(@Param("accountId") Long accountId);

    /**
     * 根据用户ID查询流水记录
     * @param userId 用户ID
     * @return 流水记录列表
     */
    @Select("SELECT * FROM virtual_account_log WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT 100")
    List<VirtualAccountLog> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据订单ID查询流水记录
     * @param orderId 订单ID
     * @return 流水记录
     */
    @Select("SELECT * FROM virtual_account_log WHERE related_order_id = #{orderId} ORDER BY create_time DESC LIMIT 1")
    VirtualAccountLog selectByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT * FROM virtual_account_log WHERE related_order_id = #{orderId} AND type = #{type} ORDER BY create_time DESC LIMIT 1")
    VirtualAccountLog selectByOrderIdAndType(@Param("orderId") Long orderId, @Param("type") String type);
}
