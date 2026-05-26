package com.atguigu.order.service;

import com.atguigu.order.bean.Order;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

public interface OrderService {

    /**
     * 创建订单（使用商品服务 + 默认内存数据）
     */
    Order createOrder(Long productId, Long userId);

    /**
     * 使用优惠券创建订单
     */
    Order createOrderWithCoupon(Long productId, Long userId, Long couponId);

    /**
     * 根据订单 ID 查询订单
     */
    Order getOrderById(Long orderId);

    /**
     * 根据用户 ID 查询该用户的所有订单（已废弃，请使用分页方法）
     * @deprecated 使用 {@link #listOrdersByUserId(Long, int, int)} 替代
     */
    @Deprecated
    List<Order> listOrdersByUserId(Long userId);

    /**
     * 根据用户 ID 分页查询订单
     * @param userId 用户ID
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页数量
     * @return 分页订单列表
     */
    IPage<Order> listOrdersByUserId(Long userId, int pageNum, int pageSize);
    
    /**
     * 根据商家 ID 查询该商家的所有订单（已废弃，请使用分页方法）
     * @deprecated 使用 {@link #listOrdersByMerchantId(Long, int, int)} 替代
     */
    @Deprecated
    List<Order> listOrdersByMerchantId(Long merchantId);

    /**
     * 根据商家 ID 分页查询订单
     * @param merchantId 商家ID
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页数量
     * @return 分页订单列表
     */
    IPage<Order> listOrdersByMerchantId(Long merchantId, int pageNum, int pageSize);

    /**
     * 支付订单（状态：CREATED -> PAID）
     * @param orderId 订单ID
     * @param userId 当前用户ID，用于所有权校验
     */
    Order payOrder(Long orderId, Long userId);

    /**
     * 商家发货（状态：PAID -> SHIPPED）
     * @param orderId 订单ID
     * @param merchantId 当前商家ID，用于所有权校验
     */
    Order shipOrder(Long orderId, Long merchantId);

    /**
     * 用户确认收货（状态：SHIPPED -> COMPLETED）
     * @param orderId 订单ID
     * @param userId 当前用户ID，用于所有权校验
     */
    Order completeOrder(Long orderId, Long userId);

    /**
     * 取消订单（状态：CREATED -> CANCELED）
     * @param orderId 订单ID
     * @param userId 当前用户ID，用于所有权校验
     */
    Order cancelOrder(Long orderId, Long userId);

    /**
     * 申请退款（状态：PAID -> REFUNDING）
     * @param orderId 订单ID
     * @param userId 当前用户ID，用于所有权校验
     */
    Order applyRefund(Long orderId, Long userId);

    /**
     * 退款成功（状态：REFUNDING -> REFUNDED）
     * @param orderId 订单ID
     * @param merchantId 当前商家ID，用于所有权校验
     */
    boolean approveRefund(Long orderId, Long merchantId);

    /**
     * 拒绝退款（状态：REFUNDING -> PAID）
     * @param orderId 订单ID
     * @param merchantId 当前商家ID，用于所有权校验
     */
    boolean rejectRefund(Long orderId, Long merchantId);

    /**
     * 售后申请（状态：COMPLETED -> REFUNDING）
     * @param orderId 订单ID
     * @param reason 申请原因
     * @param userId 当前用户ID，用于所有权校验
     */
    Order applyAfterSale(Long orderId, String reason, Long userId);

    /**
     * 批量支付订单
     * @param orderIds 订单ID列表
     * @param userId 当前用户ID，用于所有权校验
     */
    Map<Long, Order> batchPayOrders(List<Long> orderIds, Long userId);

    /**
     * 批量发货
     * @param orderIds 订单ID列表
     * @param merchantId 当前商家ID，用于所有权校验
     */
    Map<Long, Order> batchShipOrders(List<Long> orderIds, Long merchantId);

    /**
     * 批量完成订单
     * @param orderIds 订单ID列表
     * @param userId 当前用户ID，用于所有权校验
     */
    Map<Long, Order> batchCompleteOrders(List<Long> orderIds, Long userId);

    /**
     * 批量取消订单
     * @param orderIds 订单ID列表
     * @param userId 当前用户ID，用于所有权校验
     */
    Map<Long, Order> batchCancelOrders(List<Long> orderIds, Long userId);

    /**
     * 批量查询订单
     */
    List<Order> batchGetOrders(List<Long> orderIds);
    
    /**
     * 从购物车批量创建订单
     * @return 包含成功订单和失败商品信息的Map
     */
    Map<String, Object> createOrdersFromCart(Long userId);
    
    /**
     * 检查商品是否有相关订单
     */
    boolean hasOrdersForProduct(long productId);
}
