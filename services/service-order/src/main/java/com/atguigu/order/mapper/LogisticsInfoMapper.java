package com.atguigu.order.mapper;

import com.atguigu.order.bean.LogisticsInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 物流信息Mapper接口
 */
@Mapper
public interface LogisticsInfoMapper extends BaseMapper<LogisticsInfo> {
    
    /**
     * 根据订单ID查询物流信息
     * @param orderId 订单ID
     * @return 物流信息
     */
    @Select("SELECT * FROM logistics_info WHERE order_id = #{orderId}")
    LogisticsInfo selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 根据物流单号查询物流信息
     * @param trackingNo 物流单号
     * @return 物流信息
     */
    @Select("SELECT * FROM logistics_info WHERE tracking_no = #{trackingNo}")
    LogisticsInfo selectByTrackingNo(@Param("trackingNo") String trackingNo);
    
    /**
     * 根据用户ID查询物流信息列表
     * @param userId 用户ID
     * @return 物流信息列表
     */
    @Select("SELECT * FROM logistics_info WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<LogisticsInfo> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 根据商家ID查询物流信息列表
     * @param merchantId 商家ID
     * @return 物流信息列表
     */
    @Select("SELECT * FROM logistics_info WHERE merchant_id = #{merchantId} ORDER BY create_time DESC")
    List<LogisticsInfo> selectByMerchantId(@Param("merchantId") Long merchantId);
    
    /**
     * 更新物流状态
     * @param id 物流ID
     * @param status 状态
     * @return 影响行数
     */
    @Update("UPDATE logistics_info SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    
    /**
     * 更新物流单号和物流公司
     * @param id 物流ID
     * @param trackingNo 物流单号
     * @param carrier 物流公司
     * @return 影响行数
     */
    @Update("UPDATE logistics_info SET tracking_no = #{trackingNo}, carrier = #{carrier}, status = 'SHIPPED', update_time = NOW() WHERE id = #{id}")
    int updateTrackingInfo(@Param("id") Long id, @Param("trackingNo") String trackingNo, @Param("carrier") String carrier);
    
    /**
     * 更新签收信息
     * @param id 物流ID
     * @return 影响行数
     */
    @Update("UPDATE logistics_info SET status = 'DELIVERED', actual_arrival_time = NOW(), update_time = NOW() WHERE id = #{id}")
    int updateToDelivered(@Param("id") Long id);
    
    /**
     * 插入物流信息
     * @param logisticsInfo 物流信息
     * @return 影响行数
     */
    @Insert("INSERT INTO logistics_info (order_id, user_id, merchant_id, tracking_no, carrier, status, " +
            "sender_name, sender_phone, sender_address, receiver_name, receiver_phone, receiver_address, " +
            "estimated_arrival_time, create_time, update_time) " +
            "VALUES (#{orderId}, #{userId}, #{merchantId}, #{trackingNo}, #{carrier}, #{status}, " +
            "#{senderName}, #{senderPhone}, #{senderAddress}, #{receiverName}, #{receiverPhone}, #{receiverAddress}, " +
            "#{estimatedArrivalTime}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertLogisticsInfo(LogisticsInfo logisticsInfo);
    
    /**
     * 根据ID查询物流信息（带行锁，用于兜底方案）
     * @param orderId 订单ID
     * @return 物流信息
     */
    @Select("SELECT * FROM logistics_info WHERE order_id = #{orderId} FOR UPDATE")
    LogisticsInfo selectByOrderIdForUpdate(@Param("orderId") Long orderId);
}
