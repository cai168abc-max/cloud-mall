package com.atguigu.order.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.apache.seata.spring.annotation.GlobalTransactional;
import com.atguigu.common.enums.OrderStatus;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.common.mq.OrderNotifyMessage;
import com.atguigu.common.service.IdempotencyService;
import com.atguigu.order.bean.Coupon;
import com.atguigu.order.bean.Order;
import com.atguigu.order.bean.OrderItem;
import com.atguigu.order.bean.CartItem;
import com.atguigu.order.feign.ProductFeign;
import com.atguigu.order.mapper.OrderItemMapper;
import com.atguigu.order.mapper.OrderMapper;
import com.atguigu.order.service.CartService;
import com.atguigu.order.service.CouponService;
import com.atguigu.order.service.OrderService;
import com.atguigu.order.service.VirtualAccountService;
import com.atguigu.product.bean.Product;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final String ORDER_LOCK_PREFIX = "order:lock:";

    private void executeAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 10;

    private final ProductFeign productFeign;
    private final CouponService couponService;
    private final CartService cartService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final IdempotencyService idempotencyService;
    private final VirtualAccountService virtualAccountService;
    private final RedissonClient redissonClient;

    @SentinelResource(value = "createOrder", blockHandler = "createOrderFallBack")
    @Override
    @GlobalTransactional(name = "create-order", timeoutMills = 30000, rollbackFor = Exception.class)
    public Order createOrder(final Long productId, final Long userId) {
        String idempotencyKey = "order:create:" + productId + ":" + userId;
        var lockResult = idempotencyService.tryLock(idempotencyKey, 300);
        if (lockResult.isFailure()) {
            throw new IllegalStateException("订单创建中，请勿重复提交，原因: " + lockResult.getMessage());
        }
        try {
            Order order = doCreateOrder(productId, userId, null);
            final String keyRef = idempotencyKey;
            executeAfterCommit(() -> idempotencyService.releaseLock(keyRef));
            return order;
        } catch (Exception e) {
            idempotencyService.releaseLock(idempotencyKey);
            throw e;
        }
    }

    public Order createOrderFallBack(final Long productId, final Long userId, final BlockException e) {
        log.warn("订单创建被限流: productId={}, userId={}, exception={}", productId, userId, e.getClass().getSimpleName());
        throw new BusinessException(429, "系统繁忙，订单创建请求被限流，请稍后重试");
    }

    @Override
    @GlobalTransactional(name = "create-order-with-coupon", timeoutMills = 30000, rollbackFor = Exception.class)
    public Order createOrderWithCoupon(final Long productId, final Long userId, final Long couponId) {
        String idempotencyKey = "order:create:" + productId + ":" + userId + ":" + couponId;
        var lockResult = idempotencyService.tryLock(idempotencyKey, 300);
        if (lockResult.isFailure()) {
            throw new IllegalStateException("订单创建中，请勿重复提交，原因: " + lockResult.getMessage());
        }
        try {
            Order order = doCreateOrder(productId, userId, couponId);
            final String keyRef = idempotencyKey;
            executeAfterCommit(() -> idempotencyService.releaseLock(keyRef));
            return order;
        } catch (Exception e) {
            idempotencyService.releaseLock(idempotencyKey);
            throw e;
        }
    }

    private Order doCreateOrder(final Long productId, final Long userId, final Long couponId) {
        // 1. 验证商品并扣减库存
        Product product = validateProductAndDecreaseStock(productId);
        
        // 2. 创建订单实体
        Order order = createOrderEntity(product, userId);
        
        // 3. 应用优惠券
        applyCoupon(order, couponId, userId);
        
        // 4. 保存订单并发送通知
        saveOrderAndNotify(order, product);
        
        return order;
    }

    /**
     * 验证商品并扣减库存
     * @param productId 商品ID
     * @return 商品信息
     * @throws IllegalArgumentException 商品不存在或库存不足
     */
    private Product validateProductAndDecreaseStock(final Long productId) {
        Product product = productFeign.getProductById(productId);

        if (product == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        if (product.getNum() == null || product.getNum() <= 0) {
            throw new IllegalArgumentException("商品库存不足");
        }

        int updated = productFeign.decreaseStock(productId, 1);
        if (updated <= 0) {
            throw new IllegalArgumentException("库存扣减失败，商品库存不足");
        }
        
        return product;
    }

    /**
     * 创建订单实体
     * @param product 商品信息
     * @param userId 用户ID
     * @return 订单实体
     */
    private Order createOrderEntity(final Product product, final Long userId) {
        Order order = new Order();
        order.setTotalPrice(product.getPrice());
        order.setUserId(userId);
        order.setMerchantId(product.getMerchantId());
        order.setNickName("用户-" + userId);
        order.setAddress("默认地址");
        order.setProductList(List.of(product));
        order.setStatus(OrderStatus.CREATED);
        order.setDiscountAmount(BigDecimal.ZERO);
        return order;
    }

    /**
     * 应用优惠券
     * @param order 订单实体
     * @param couponId 优惠券ID
     * @param userId 用户ID
     */
    private void applyCoupon(final Order order, final Long couponId, final Long userId) {
        if (couponId == null) {
            order.setPayAmount(order.getTotalPrice().subtract(order.getDiscountAmount()));
            return;
        }
        
        Coupon coupon = couponService.getValidCouponForUse(couponId, userId);
        if (coupon != null && order.getTotalPrice().compareTo(coupon.getThreshold()) >= 0) {
            boolean acquired = couponService.acquireCoupon(couponId, userId);
            if (!acquired) {
                throw new BusinessException("优惠券已被领完或已使用");
            }
            order.setCouponId(couponId);
            order.setDiscountAmount(coupon.getAmount());
        }
        
        BigDecimal payAmount = order.getTotalPrice().subtract(order.getDiscountAmount());
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            payAmount = BigDecimal.ZERO;
        }
        order.setPayAmount(payAmount);
    }

    /**
     * 保存订单并发送通知
     * @param order 订单实体
     * @param product 商品信息
     */
    private void saveOrderAndNotify(Order order, Product product) {
        orderMapper.insertOrder(order);
        
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setProductId(product.getId());
        orderItem.setProductName(product.getName());
        orderItem.setPrice(product.getPrice());
        orderItem.setQuantity(1);
        orderItemMapper.insertOrderItem(orderItem);

        // 发送订单通知（事务提交后执行）
        final Order orderRef = order;
        executeAfterCommit(() -> sendOrderNotification(orderRef));
    }

    private void sendOrderNotification(final Order order) {
        try {
            rocketMQTemplate.asyncSend("order-notify-topic",
                OrderNotifyMessage.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .status(order.getStatus().name())
                    .message("订单创建成功")
                    .build(),
                new org.apache.rocketmq.client.producer.SendCallback() {
                    @Override
                    public void onSuccess(final org.apache.rocketmq.client.producer.SendResult sendResult) {
                        log.info("订单通知消息发送成功: {}", sendResult);
                    }
                    @Override
                    public void onException(final Throwable e) {
                        log.error("订单通知消息发送失败", e);
                    }
                });
        } catch (Exception e) {
            log.error("发送订单通知失败", e);
        }
    }

    @Override
    public Order getOrderById(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    /**
     * @deprecated 使用分页方法替代
     */
    @Override
    @Deprecated
    public List<Order> listOrdersByUserId(final Long userId) {
        // 兼容旧接口，默认返回第一页10条数据
        return listOrdersByUserId(userId, 1, 10).getRecords();
    }

    @Override
    public IPage<Order> listOrdersByUserId(final Long userId, final int pageNum, final int pageSize) {
        Page<Order> page = new Page<>(pageNum, pageSize);
        return orderMapper.selectByUserId(page, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public Order payOrder(final Long orderId, final Long userId) {
        String lockKey = ORDER_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Order order = orderMapper.selectById(orderId);
                if (order == null) {
                    throw new BusinessException("订单不存在");
                }
                if (order.getStatus() != OrderStatus.CREATED) {
                    throw new BusinessException("订单状态异常，无法支付");
                }
                if (!order.getUserId().equals(userId)) {
                    throw new SecurityException("无权操作此订单");
                }

                String transactionNo = "PAY_" + orderId + "_" + System.currentTimeMillis();
                try {
                    virtualAccountService.pay(userId, order.getPayAmount(), orderId, transactionNo);
                } catch (BusinessException e) {
                    throw new BusinessException("支付失败: " + e.getMessage());
                }

                order.setStatus(OrderStatus.PAID);
                orderMapper.updateStatusToPaid(orderId, OrderStatus.PAID.name());

                log.info("订单支付成功, orderId={}, userId={}, payAmount={}", orderId, userId, order.getPayAmount());

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                return order;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order shipOrder(final Long orderId, final Long merchantId) {
        String lockKey = ORDER_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Order order = orderMapper.selectById(orderId);
                if (order == null || order.getStatus() != OrderStatus.PAID) {
                    return null;
                }
                if (!order.getMerchantId().equals(merchantId)) {
                    throw new SecurityException("无权操作此订单");
                }
                order.setStatus(OrderStatus.SHIPPED);
                orderMapper.updateStatusToShipped(orderId, OrderStatus.SHIPPED.name());

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                return order;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order completeOrder(Long orderId, Long userId) {
        String lockKey = ORDER_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Order order = orderMapper.selectById(orderId);
                if (order == null || order.getStatus() != OrderStatus.SHIPPED) {
                    return null;
                }
                if (!order.getUserId().equals(userId)) {
                    throw new SecurityException("无权操作此订单");
                }
                order.setStatus(OrderStatus.COMPLETED);
                orderMapper.updateStatusToCompleted(orderId, OrderStatus.COMPLETED.name());

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                return order;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @GlobalTransactional(name = "cancel-order", timeoutMills = 30000, rollbackFor = Exception.class)
    public Order cancelOrder(final Long orderId, final Long userId) {
        String lockKey = ORDER_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Order order = orderMapper.selectById(orderId);
                if (order == null || order.getStatus() != OrderStatus.CREATED) {
                    return null;
                }
                if (!order.getUserId().equals(userId)) {
                    throw new SecurityException("无权操作此订单");
                }

                List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
                List<Map<String, Object>> rollbackItems = orderItems.stream()
                        .map(item -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("productId", item.getProductId());
                            map.put("quantity", item.getQuantity());
                            return map;
                        })
                        .collect(Collectors.toList());
                productFeign.batchIncreaseStock(rollbackItems);

                order.setStatus(OrderStatus.CANCELED);
                orderMapper.updateStatus(orderId, OrderStatus.CANCELED.name());

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                return order;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order applyRefund(final Long orderId, final Long userId) {
        String lockKey = ORDER_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Order order = orderMapper.selectById(orderId);
                if (order == null || order.getStatus() != OrderStatus.PAID) {
                    return null;
                }
                if (!order.getUserId().equals(userId)) {
                    throw new SecurityException("无权操作此订单");
                }
                order.setStatus(OrderStatus.REFUNDING);
                orderMapper.updateStatus(orderId, OrderStatus.REFUNDING.name());

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                return order;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @GlobalTransactional(name = "approve-refund", timeoutMills = 30000, rollbackFor = Exception.class)
    public boolean approveRefund(final Long orderId, final Long merchantId) {
        String lockKey = ORDER_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Order order = orderMapper.selectById(orderId);
                if (order == null || order.getStatus() != OrderStatus.REFUNDING) {
                    return false;
                }
                if (!order.getMerchantId().equals(merchantId)) {
                    throw new SecurityException("无权操作此订单");
                }

                List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
                List<Map<String, Object>> rollbackItems = orderItems.stream()
                        .map(item -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("productId", item.getProductId());
                            map.put("quantity", item.getQuantity());
                            return map;
                        })
                        .collect(Collectors.toList());
                productFeign.batchIncreaseStock(rollbackItems);

                String transactionNo = "REFUND_" + orderId + "_" + System.currentTimeMillis();
                try {
                    virtualAccountService.refund(order.getUserId(), order.getPayAmount(), orderId, transactionNo);
                } catch (BusinessException e) {
                    log.error("退款失败, orderId={}, error={}", orderId, e.getMessage());
                    throw new BusinessException("退款失败: " + e.getMessage());
                }

                order.setStatus(OrderStatus.REFUNDED);
                orderMapper.updateStatus(orderId, OrderStatus.REFUNDED.name());

                log.info("订单退款成功, orderId={}, userId={}, refundAmount={}", orderId, order.getUserId(), order.getPayAmount());

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                return true;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public boolean rejectRefund(Long orderId, Long merchantId) {
        String lockKey = ORDER_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Order order = orderMapper.selectById(orderId);
                if (order == null || order.getStatus() != OrderStatus.REFUNDING) {
                    return false;
                }
                if (!order.getMerchantId().equals(merchantId)) {
                    throw new SecurityException("无权操作此订单");
                }
                order.setStatus(OrderStatus.PAID);
                orderMapper.updateStatus(orderId, OrderStatus.PAID.name());

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                return true;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public Order applyAfterSale(final Long orderId, final String reason, final Long userId) {
        String lockKey = ORDER_LOCK_PREFIX + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Order order = orderMapper.selectById(orderId);
                if (order == null || order.getStatus() != OrderStatus.COMPLETED) {
                    return null;
                }
                if (!order.getUserId().equals(userId)) {
                    throw new SecurityException("无权操作此订单");
                }
                order.setStatus(OrderStatus.REFUNDING);
                orderMapper.updateStatus(orderId, OrderStatus.REFUNDING.name());

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                return order;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    /**
     * @deprecated 使用分页方法替代
     */
    @Override
    @Deprecated
    public List<Order> listOrdersByMerchantId(Long merchantId) {
        // 兼容旧接口，默认返回第一页10条数据
        return listOrdersByMerchantId(merchantId, 1, 10).getRecords();
    }

    @Override
    public IPage<Order> listOrdersByMerchantId(final Long merchantId, final int pageNum, final int pageSize) {
        Page<Order> page = new Page<>(pageNum, pageSize);
        return orderMapper.selectByMerchantId(page, merchantId);
    }

    @Override
    public Map<Long, Order> batchPayOrders(List<Long> orderIds, Long userId) {
        String lockKey = "batch-pay:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Map<Long, Order> result = new HashMap<>();
                if (orderIds == null || orderIds.isEmpty()) {
                    return result;
                }
                
                List<Order> orders = orderMapper.batchSelectByIdsAndStatus(orderIds, OrderStatus.CREATED.name());
                
                if (orders.isEmpty()) {
                    return result;
                }
                
                List<Long> validOrderIds = new ArrayList<>();
                for (Order order : orders) {
                    if (order.getUserId().equals(userId)) {
                        String transactionNo = "BATCH_PAY_" + order.getId() + "_" + System.currentTimeMillis();
                        try {
                            virtualAccountService.pay(userId, order.getPayAmount(), order.getId(), transactionNo);
                            validOrderIds.add(order.getId());
                        } catch (BusinessException e) {
                            log.warn("批量支付中订单支付失败, orderId={}, error={}", order.getId(), e.getMessage());
                        }
                    }
                }
                
                if (validOrderIds.isEmpty()) {
                    return result;
                }
                
                doBatchUpdateStatusToPaid(validOrderIds);
                
                for (Order order : orders) {
                    if (validOrderIds.contains(order.getId())) {
                        order.setStatus(OrderStatus.PAID);
                        result.put(order.getId(), order);
                    }
                }

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                
                return result;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Transactional(rollbackFor = Exception.class, timeout = 60,
                   propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void doBatchUpdateStatusToPaid(List<Long> validOrderIds) {
        orderMapper.batchUpdateStatusToPaid(validOrderIds, OrderStatus.PAID.name());
    }

    @Override
    @Transactional(rollbackFor = Exception.class,
                   isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<Long, Order> batchShipOrders(final List<Long> orderIds, final Long merchantId) {
        String lockKey = "batch-ship:" + merchantId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Map<Long, Order> result = new HashMap<>();
                if (orderIds == null || orderIds.isEmpty()) {
                    return result;
                }
                
                List<Order> orders = orderMapper.batchSelectByIdsAndStatus(orderIds, OrderStatus.PAID.name());
                
                if (orders.isEmpty()) {
                    return result;
                }
                
                List<Long> validOrderIds = orders.stream()
                        .filter(order -> order.getMerchantId().equals(merchantId))
                        .map(Order::getId)
                        .collect(Collectors.toList());
                
                if (validOrderIds.isEmpty()) {
                    return result;
                }
                
                orderMapper.batchUpdateStatusToShipped(validOrderIds, OrderStatus.SHIPPED.name());
                
                for (Order order : orders) {
                    if (order.getMerchantId().equals(merchantId)) {
                        order.setStatus(OrderStatus.SHIPPED);
                        result.put(order.getId(), order);
                    }
                }

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                
                return result;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 60,
                   propagation = org.springframework.transaction.annotation.Propagation.REQUIRED,
                   isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<Long, Order> batchCompleteOrders(final List<Long> orderIds, final Long userId) {
        String lockKey = "batch-complete:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Map<Long, Order> result = new HashMap<>();
                if (orderIds == null || orderIds.isEmpty()) {
                    return result;
                }
                
                List<Order> orders = orderMapper.batchSelectByIdsAndStatus(orderIds, OrderStatus.SHIPPED.name());
                
                if (orders.isEmpty()) {
                    return result;
                }
                
                List<Long> validOrderIds = orders.stream()
                        .filter(order -> order.getUserId().equals(userId))
                        .map(Order::getId)
                        .collect(Collectors.toList());
                
                if (validOrderIds.isEmpty()) {
                    return result;
                }
                
                orderMapper.batchUpdateStatusToCompleted(validOrderIds, OrderStatus.COMPLETED.name());
                
                for (Order order : orders) {
                    if (order.getUserId().equals(userId)) {
                        order.setStatus(OrderStatus.COMPLETED);
                        result.put(order.getId(), order);
                    }
                }

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                
                return result;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @GlobalTransactional(name = "batch-cancel-orders", rollbackFor = Exception.class)
    public Map<Long, Order> batchCancelOrders(List<Long> orderIds, Long userId) {
        String lockKey = "batch-cancel:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            try {
                Map<Long, Order> result = new HashMap<>();
                if (orderIds == null || orderIds.isEmpty()) {
                    return result;
                }
                
                List<Order> orders = orderMapper.batchSelectByIdsAndStatus(orderIds, OrderStatus.CREATED.name());
                
                if (orders.isEmpty()) {
                    return result;
                }
                
                List<Order> userOrders = orders.stream()
                        .filter(order -> order.getUserId().equals(userId))
                        .toList();
                
                if (userOrders.isEmpty()) {
                    return result;
                }
                
                for (Order order : userOrders) {
                    List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
                    if (!orderItems.isEmpty()) {
                        List<Map<String, Object>> rollbackItems = orderItems.stream()
                                .map(item -> {
                                    Map<String, Object> map = new HashMap<>();
                                    map.put("productId", item.getProductId());
                                    map.put("quantity", item.getQuantity());
                                    return map;
                                })
                                .collect(Collectors.toList());
                        productFeign.batchIncreaseStock(rollbackItems);
                    }
                }
                
                List<Long> validOrderIds = userOrders.stream()
                        .map(Order::getId)
                        .collect(Collectors.toList());
                
                orderMapper.batchUpdateStatus(validOrderIds, OrderStatus.CANCELED.name());
                
                for (Order order : userOrders) {
                    order.setStatus(OrderStatus.CANCELED);
                    result.put(order.getId(), order);
                }

                executeAfterCommit(() -> {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                });
                
                return result;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    public List<Order> batchGetOrders(final List<Long> orderIds) {
        return orderMapper.batchSelectByIds(orderIds);
    }

    /**
     * 从购物车创建订单（批量优化版本）
     * 性能优化：
     * 1. 批量获取商品信息
     * 2. 批量扣减库存
     * 3. 批量创建订单和订单项
     * 注意：此方法不使用全局事务，每个订单独立处理，失败不影响其他订单
     */
    @Override
    @GlobalTransactional(name = "create-orders-from-cart", rollbackFor = Exception.class)
    @SuppressFBWarnings("UC_USELESS_OBJECT")
    public Map<String, Object> createOrdersFromCart(final Long userId) {
        Map<String, Object> result = new HashMap<>();
        List<Order> successOrders = new ArrayList<>();
        List<Map<String, Object>> failedItems = new ArrayList<>();

        List<CartItem> checkedItems = cartService.getCheckedItems(userId);
        if (checkedItems == null || checkedItems.isEmpty()) {
            result.put("successOrders", successOrders);
            result.put("failedItems", failedItems);
            return result;
        }

        // 准备商品数据并扣减库存
        Map<Long, Product> productMap = prepareProductsAndDeductStock(checkedItems, failedItems);
        if (productMap == null) {
            result.put("successOrders", successOrders);
            result.put("failedItems", failedItems);
            return result;
        }

        // 按购物车项创建订单（每个商品一个订单）
        for (CartItem item : checkedItems) {
            Long productId = item.getProductId();
            Product product = productMap.get(productId);
            
            if (product == null || failedItems.stream().anyMatch(f -> f.get("productId").equals(productId))) {
                continue;
            }
            
            try {
                // 创建订单
                Order order = new Order();
                order.setTotalPrice(product.getPrice().multiply(new BigDecimal(item.getQuantity())));
                order.setUserId(userId);
                order.setMerchantId(product.getMerchantId());
                order.setNickName("用户-" + userId);
                order.setAddress("默认地址");
                order.setStatus(OrderStatus.CREATED);
                order.setDiscountAmount(BigDecimal.ZERO);
                order.setPayAmount(order.getTotalPrice());

                orderMapper.insertOrder(order);
                successOrders.add(order);

                // 创建订单项
                OrderItem orderItem = new OrderItem();
                orderItem.setOrderId(order.getId());
                orderItem.setProductId(product.getId());
                orderItem.setProductName(product.getName());
                orderItem.setPrice(product.getPrice());
                orderItem.setQuantity(item.getQuantity());
                orderItemMapper.insertOrderItem(orderItem);

                final Order orderRef = order;
                executeAfterCommit(() -> sendOrderNotification(orderRef));
                
            } catch (Exception e) {
                // 订单创建失败，需要回滚库存
                Map<String, Object> rollbackItem = new HashMap<>();
                rollbackItem.put("productId", productId);
                rollbackItem.put("quantity", item.getQuantity());
                try {
                    productFeign.batchIncreaseStock(List.of(rollbackItem));
                } catch (Exception rollbackEx) {
                    org.slf4j.LoggerFactory.getLogger(OrderServiceImpl.class)
                        .error("库存回滚失败，productId: {}, quantity: {}", productId, item.getQuantity(), rollbackEx);
                }
                
                Map<String, Object> failedItem = new HashMap<>();
                failedItem.put("productId", productId);
                failedItem.put("reason", "订单创建失败: " + e.getMessage());
                failedItems.add(failedItem);
            }
        }

        // 清空已处理的购物车项（事务提交后执行，防止回滚时购物车数据丢失）
        if (!successOrders.isEmpty()) {
            final Long userIdRef = userId;
            executeAfterCommit(() -> cartService.clearCheckedItems(userIdRef));
        }

        result.put("successOrders", successOrders);
        result.put("failedItems", failedItems);
        return result;
    }

    @Override
    public boolean hasOrdersForProduct(final long productId) {
        long count = orderItemMapper.countByProductId(productId);
        return count > 0;
    }

    /**
     * 准备商品数据并批量扣减库存。
     * @param checkedItems 选中的购物车项
     * @param failedItems 失败项列表（会被修改）
     * @return 商品ID到商品的映射，如果处理失败返回null
     */
    private Map<Long, Product> prepareProductsAndDeductStock(final List<CartItem> checkedItems,
                                                              final List<Map<String, Object>> failedItems) {
        // 按商品分组，合并相同商品的购买数量
        Map<Long, Integer> productQuantityMap = new HashMap<>();
        for (CartItem item : checkedItems) {
            productQuantityMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        // 批量获取商品信息（性能优化：减少N+1调用）
        List<Long> productIds = new ArrayList<>(productQuantityMap.keySet());
        Map<Long, Product> productMap = new HashMap<>();

        try {
            List<Product> products = productFeign.batchGetProducts(productIds);
            if (products != null) {
                for (Product product : products) {
                    productMap.put(product.getId(), product);
                }
            }
        } catch (Exception e) {
            log.error("批量获取商品信息失败", e);
            for (Long productId : productIds) {
                Map<String, Object> failedItem = new HashMap<>();
                failedItem.put("productId", productId);
                failedItem.put("reason", "获取商品信息失败: " + e.getMessage());
                failedItems.add(failedItem);
            }
            return null;
        }

        // 校验库存并批量扣减
        List<Map<String, Object>> stockDeductItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : productQuantityMap.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();
            Product product = productMap.get(productId);

            if (product == null) {
                Map<String, Object> failedItem = new HashMap<>();
                failedItem.put("productId", productId);
                failedItem.put("reason", "商品不存在");
                failedItems.add(failedItem);
                continue;
            }

            if (product.getNum() == null || product.getNum() < quantity) {
                Map<String, Object> failedItem = new HashMap<>();
                failedItem.put("productId", productId);
                failedItem.put("reason", "库存不足，当前库存: " + product.getNum() + "，需要: " + quantity);
                failedItems.add(failedItem);
                continue;
            }

            Map<String, Object> stockItem = new HashMap<>();
            stockItem.put("productId", productId);
            stockItem.put("quantity", quantity);
            stockDeductItems.add(stockItem);
        }

        // 批量扣减库存
        if (!stockDeductItems.isEmpty()) {
            try {
                productFeign.batchDecreaseStock(stockDeductItems);
            } catch (Exception e) {
                for (Map<String, Object> item : stockDeductItems) {
                    Map<String, Object> failedItem = new HashMap<>();
                    failedItem.put("productId", item.get("productId"));
                    failedItem.put("reason", "库存扣减失败: " + e.getMessage());
                    failedItems.add(failedItem);
                }
                return null;
            }
        }

        return productMap;
    }
}
