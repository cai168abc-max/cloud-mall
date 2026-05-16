package com.atguigu.order.service;

import com.atguigu.order.bean.LogisticsInfo;
import com.atguigu.order.bean.LogisticsTrace;

import java.util.List;

/**
 * 物流服务接口
 */
public interface LogisticsService {
    
    /**
     * 发货（创建物流信息）
     * 包含：订单状态校验、分布式锁、创建物流信息、更新订单状态、MQ通知
     * 
     * @param orderId 订单ID
     * @param merchantId 商家ID（用于权限校验）
     * @param trackingNo 物流单号
     * @param carrier 物流公司
     * @param senderName 发件人姓名
     * @param senderPhone 发件人电话
     * @param senderAddress 发件人地址
     * @return 物流信息
     * @throws com.atguigu.common.exception.BusinessException 订单未支付/已发货/获取锁失败等
     */
    LogisticsInfo shipOrder(Long orderId, Long merchantId, String trackingNo, String carrier,
                           String senderName, String senderPhone, String senderAddress);
    
    /**
     * 根据订单ID查询物流信息（带Redis缓存）
     * 
     * @param orderId 订单ID
     * @return 物流信息（包含轨迹列表）
     */
    LogisticsInfo getLogisticsByOrderId(Long orderId);
    
    /**
     * 根据物流单号查询物流信息
     * 
     * @param trackingNo 物流单号
     * @return 物流信息（包含轨迹列表）
     */
    LogisticsInfo getLogisticsByTrackingNo(String trackingNo);
    
    /**
     * 查询用户的物流列表
     * 
     * @param userId 用户ID
     * @return 物流信息列表
     */
    List<LogisticsInfo> listLogisticsByUserId(Long userId);
    
    /**
     * 查询商家的物流列表
     * 
     * @param merchantId 商家ID
     * @return 物流信息列表
     */
    List<LogisticsInfo> listLogisticsByMerchantId(Long merchantId);
    
    /**
     * 更新物流轨迹
     * 
     * @param logisticsId 物流ID
     * @param traceTime 轨迹时间
     * @param status 轨迹状态
     * @param location 所在地点
     * @param description 轨迹描述
     * @param operator 操作人/网点
     * @return 是否成功
     */
    boolean addLogisticsTrace(Long logisticsId, String traceTime, String status, 
                              String location, String description, String operator);
    
    /**
     * 确认签收
     * 
     * @param orderId 订单ID
     * @param userId 用户ID（用于权限校验）
     * @return 是否成功
     */
    boolean confirmDelivery(Long orderId, Long userId);
    
    /**
     * 刷新物流缓存
     * 
     * @param orderId 订单ID
     */
    void refreshLogisticsCache(Long orderId);
}
