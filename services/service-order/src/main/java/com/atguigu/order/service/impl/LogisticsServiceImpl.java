package com.atguigu.order.service.impl;

import com.atguigu.common.cache.CacheKeyConstants;
import com.atguigu.common.enums.LogisticsStatus;
import com.atguigu.common.enums.OrderStatus;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.common.mq.OrderNotifyMessage;
import com.atguigu.order.bean.LogisticsInfo;
import com.atguigu.order.bean.LogisticsTrace;
import com.atguigu.order.bean.Order;
import com.atguigu.order.mapper.LogisticsInfoMapper;
import com.atguigu.order.mapper.LogisticsTraceMapper;
import com.atguigu.order.mapper.OrderMapper;
import com.atguigu.order.service.LogisticsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 物流服务实现类
 * 
 * 核心功能：
 * 1. 发货：订单状态校验、分布式锁、创建物流信息、更新订单状态、MQ通知
 * 2. 物流查询：Redis缓存（30分钟过期）
 * 3. 兜底方案：分布式锁不可用时使用数据库行锁
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class LogisticsServiceImpl implements LogisticsService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsServiceImpl.class);
    
    private static final long CACHE_EXPIRE_MINUTES = 30;
    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 5;
    
    private final LogisticsInfoMapper logisticsInfoMapper;
    private final LogisticsTraceMapper logisticsTraceMapper;
    private final OrderMapper orderMapper;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogisticsInfo shipOrder(final Long orderId, final Long merchantId, final String trackingNo, final String carrier,
                                   final String senderName, final String senderPhone, final String senderAddress) {
        log.info("发货请求: orderId={}, merchantId={}, trackingNo={}, carrier={}", 
                orderId, merchantId, trackingNo, carrier);
        
        // 1. 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        
        // 2. 权限校验：只有订单所属商家才能发货
        if (!order.getMerchantId().equals(merchantId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
        
        // 3. 状态校验：只有已支付的订单才能发货
        if (order.getStatus() != OrderStatus.PAID) {
            if (order.getStatus() == OrderStatus.SHIPPED) {
                throw new BusinessException(400, "订单已发货，请勿重复操作");
            } else if (order.getStatus() == OrderStatus.CREATED) {
                throw new BusinessException(400, "订单未支付，无法发货");
            } else {
                throw new BusinessException(400, "订单状态异常，当前状态: " + order.getStatus());
            }
        }
        
        // 4. 检查是否已存在物流信息
        LogisticsInfo existingLogistics = logisticsInfoMapper.selectByOrderId(orderId);
        if (existingLogistics != null) {
            throw new BusinessException(400, "物流信息已存在，请勿重复发货");
        }
        
        // 5. 使用分布式锁防止并发发货
        String lockKey = CacheKeyConstants.orderShipLock(orderId);
        RLock lock = redissonClient.getLock(lockKey);
        
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "系统繁忙，请稍后重试");
        }

        try {
            if (locked) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                });
                return doShipOrder(order, trackingNo, carrier, senderName, senderPhone, senderAddress);
            } else {
                return doShipOrderWithDbLock(order, trackingNo, carrier, senderName, senderPhone, senderAddress);
            }
        } catch (Exception e) {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(500, "发货失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行发货逻辑（分布式锁保护）
     */
    private LogisticsInfo doShipOrder(final Order order, final String trackingNo, final String carrier,
                                      final String senderName, final String senderPhone, final String senderAddress) {
        // 双重检查：再次确认订单状态
        Order freshOrder = orderMapper.selectById(order.getId());
        if (freshOrder.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(400, "订单状态已变更，请刷新后重试");
        }
        
        // 再次检查物流信息
        LogisticsInfo existingLogistics = logisticsInfoMapper.selectByOrderId(order.getId());
        if (existingLogistics != null) {
            throw new BusinessException(400, "物流信息已存在，请勿重复发货");
        }
        
        // 创建物流信息
        LogisticsInfo logisticsInfo = createLogisticsInfo(order, trackingNo, carrier, 
                senderName, senderPhone, senderAddress);
        logisticsInfoMapper.insertLogisticsInfo(logisticsInfo);
        
        // 创建初始物流轨迹
        createInitialTrace(logisticsInfo.getId(), carrier);
        
        // 更新订单状态为已发货
        orderMapper.updateStatusToShipped(order.getId(), OrderStatus.SHIPPED.name());
        
        // 发送MQ通知（事务提交后执行）
        final Order orderRef = order;
        final LogisticsInfo logisticsRef = logisticsInfo;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendShipNotification(orderRef, logisticsRef);
                cacheLogisticsInfo(logisticsRef);
            }
        });
        
        log.info("发货成功: orderId={}, logisticsId={}", order.getId(), logisticsInfo.getId());
        return logisticsInfo;
    }
    
    /**
     * 使用数据库行锁兜底执行发货逻辑
     */
    private LogisticsInfo doShipOrderWithDbLock(final Order order, final String trackingNo, final String carrier,
                                                final String senderName, final String senderPhone, final String senderAddress) {
        // 使用SELECT FOR UPDATE获取行锁
        LogisticsInfo existingLogistics = logisticsInfoMapper.selectByOrderIdForUpdate(order.getId());
        if (existingLogistics != null) {
            throw new BusinessException(400, "物流信息已存在，请勿重复发货");
        }
        
        // 再次确认订单状态
        Order freshOrder = orderMapper.selectById(order.getId());
        if (freshOrder.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(400, "订单状态已变更，请刷新后重试");
        }
        
        // 创建物流信息
        LogisticsInfo logisticsInfo = createLogisticsInfo(order, trackingNo, carrier, 
                senderName, senderPhone, senderAddress);
        logisticsInfoMapper.insertLogisticsInfo(logisticsInfo);
        
        // 创建初始物流轨迹
        createInitialTrace(logisticsInfo.getId(), carrier);
        
        // 更新订单状态为已发货
        orderMapper.updateStatusToShipped(order.getId(), OrderStatus.SHIPPED.name());
        
        // 发送MQ通知（事务提交后执行）
        final Order orderRef = order;
        final LogisticsInfo logisticsRef = logisticsInfo;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendShipNotification(orderRef, logisticsRef);
                cacheLogisticsInfo(logisticsRef);
            }
        });
        
        log.info("发货成功(数据库行锁兜底): orderId={}, logisticsId={}", order.getId(), logisticsInfo.getId());
        return logisticsInfo;
    }
    
    /**
     * 创建物流信息实体
     */
    private LogisticsInfo createLogisticsInfo(final Order order, final String trackingNo, final String carrier,
                                              final String senderName, final String senderPhone, final String senderAddress) {
        return LogisticsInfo.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .merchantId(order.getMerchantId())
                .trackingNo(trackingNo)
                .carrier(carrier)
                .status(LogisticsStatus.SHIPPED)
                .senderName(senderName)
                .senderPhone(senderPhone)
                .senderAddress(senderAddress)
                .receiverName(order.getNickName())
                .receiverAddress(order.getAddress())
                .estimatedArrivalTime(LocalDateTime.now().plusDays(3)) // 默认预计3天到达
                .build();
    }
    
    /**
     * 创建初始物流轨迹
     */
    private void createInitialTrace(final Long logisticsId, final String carrier) {
        LogisticsTrace trace = LogisticsTrace.builder()
                .logisticsId(logisticsId)
                .traceTime(LocalDateTime.now())
                .status("已揽收")
                .location("商家仓库")
                .description("商品已发货，物流公司: " + carrier + " 已揽收")
                .operator(carrier)
                .build();
        logisticsTraceMapper.insertLogisticsTrace(trace);
    }
    
    /**
     * 发送发货通知MQ消息
     */
    private void sendShipNotification(final Order order, final LogisticsInfo logisticsInfo) {
        try {
            OrderNotifyMessage message = OrderNotifyMessage.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .status(OrderStatus.SHIPPED.name())
                    .message("订单已发货，物流单号: " + logisticsInfo.getTrackingNo())
                    .build();
            
            rocketMQTemplate.asyncSend("order-ship-topic", message, new SendCallback() {
                @Override
                public void onSuccess(final SendResult sendResult) {
                    log.info("发货通知消息发送成功: orderId={}, msgId={}", order.getId(), sendResult.getMsgId());
                }
                
                @Override
                public void onException(final Throwable e) {
                    log.error("发货通知消息发送失败: orderId={}", order.getId(), e);
                }
            });
        } catch (Exception e) {
            log.error("发送发货通知失败: orderId={}", order.getId(), e);
        }
    }
    
    /**
     * 缓存物流信息
     */
    private void cacheLogisticsInfo(final LogisticsInfo logisticsInfo) {
        try {
            String cacheKey = CacheKeyConstants.logisticsInfo(logisticsInfo.getOrderId());
            String json = objectMapper.writeValueAsString(logisticsInfo);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("缓存物流信息: key={}", cacheKey);
        } catch (JsonProcessingException e) {
            log.error("序列化物流信息失败: logisticsId={}", logisticsInfo.getId(), e);
        }
    }

    @Override
    public LogisticsInfo getLogisticsByOrderId(final Long orderId) {
        // 1. 先查Redis缓存
        String cacheKey = CacheKeyConstants.logisticsInfo(orderId);
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        
        if (cachedJson != null) {
            try {
                LogisticsInfo logisticsInfo = objectMapper.readValue(cachedJson, LogisticsInfo.class);
                // 查询物流轨迹
                List<LogisticsTrace> traceList = logisticsTraceMapper.selectByLogisticsId(logisticsInfo.getId());
                logisticsInfo.setTraceList(traceList);
                log.debug("从缓存获取物流信息: orderId={}", orderId);
                return logisticsInfo;
            } catch (JsonProcessingException e) {
                log.error("反序列化物流信息失败: key={}", cacheKey, e);
            }
        }
        
        // 2. 缓存未命中，查询数据库
        LogisticsInfo logisticsInfo = logisticsInfoMapper.selectByOrderId(orderId);
        if (logisticsInfo == null) {
            return null;
        }
        
        // 查询物流轨迹
        List<LogisticsTrace> traceList = logisticsTraceMapper.selectByLogisticsId(logisticsInfo.getId());
        logisticsInfo.setTraceList(traceList);
        
        // 3. 写入缓存
        try {
            String json = objectMapper.writeValueAsString(logisticsInfo);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("物流信息写入缓存: orderId={}", orderId);
        } catch (JsonProcessingException e) {
            log.error("序列化物流信息失败: orderId={}", orderId, e);
        }
        
        return logisticsInfo;
    }

    @Override
    public LogisticsInfo getLogisticsByTrackingNo(final String trackingNo) {
        LogisticsInfo logisticsInfo = logisticsInfoMapper.selectByTrackingNo(trackingNo);
        if (logisticsInfo == null) {
            return null;
        }
        
        // 查询物流轨迹
        List<LogisticsTrace> traceList = logisticsTraceMapper.selectByLogisticsId(logisticsInfo.getId());
        logisticsInfo.setTraceList(traceList);
        
        return logisticsInfo;
    }

    @Override
    public List<LogisticsInfo> listLogisticsByUserId(final Long userId) {
        List<LogisticsInfo> logisticsList = logisticsInfoMapper.selectByUserId(userId);
        // 为每个物流信息加载轨迹
        logisticsList.forEach(logistics -> {
            List<LogisticsTrace> traceList = logisticsTraceMapper.selectByLogisticsId(logistics.getId());
            logistics.setTraceList(traceList);
        });
        return logisticsList;
    }

    @Override
    public List<LogisticsInfo> listLogisticsByMerchantId(final Long merchantId) {
        List<LogisticsInfo> logisticsList = logisticsInfoMapper.selectByMerchantId(merchantId);
        // 为每个物流信息加载轨迹
        logisticsList.forEach(logistics -> {
            List<LogisticsTrace> traceList = logisticsTraceMapper.selectByLogisticsId(logistics.getId());
            logistics.setTraceList(traceList);
        });
        return logisticsList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addLogisticsTrace(final Long logisticsId, final String traceTime, final String status, 
                                     final String location, final String description, final String operator) {
        LogisticsInfo logisticsInfo = logisticsInfoMapper.selectById(logisticsId);
        if (logisticsInfo == null) {
            throw new BusinessException(404, "物流信息不存在");
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime time = LocalDateTime.parse(traceTime, formatter);
        
        LogisticsTrace trace = LogisticsTrace.builder()
                .logisticsId(logisticsId)
                .traceTime(time)
                .status(status)
                .location(location)
                .description(description)
                .operator(operator)
                .build();
        
        int result = logisticsTraceMapper.insertLogisticsTrace(trace);
        
        String cacheKey = CacheKeyConstants.logisticsInfo(logisticsInfo.getOrderId());
        if ("已签收".equals(status) || "已妥投".equals(status)) {
            logisticsInfoMapper.updateToDelivered(logisticsId);
            final String key = cacheKey;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stringRedisTemplate.delete(key);
                }
            });
        } else if ("运输中".equals(status) || "派送中".equals(status)) {
            logisticsInfoMapper.updateStatus(logisticsId, LogisticsStatus.IN_TRANSIT.name());
            final String key = cacheKey;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stringRedisTemplate.delete(key);
                }
            });
        }
        
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmDelivery(final Long orderId, final Long userId) {
        // 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        
        // 权限校验
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
        
        // 状态校验
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new BusinessException(400, "订单状态异常，无法确认收货");
        }
        
        // 查询物流信息
        LogisticsInfo logisticsInfo = logisticsInfoMapper.selectByOrderId(orderId);
        if (logisticsInfo == null) {
            throw new BusinessException(404, "物流信息不存在");
        }
        
        // 更新物流状态
        logisticsInfoMapper.updateToDelivered(logisticsInfo.getId());
        
        // 更新订单状态
        orderMapper.updateStatusToCompleted(orderId, OrderStatus.COMPLETED.name());
        
        final String cacheKey = CacheKeyConstants.logisticsInfo(orderId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stringRedisTemplate.delete(cacheKey);
            }
        });
        
        log.info("确认签收成功: orderId={}, userId={}", orderId, userId);
        return true;
    }

    @Override
    public void refreshLogisticsCache(final Long orderId) {
        String cacheKey = CacheKeyConstants.logisticsInfo(orderId);
        stringRedisTemplate.delete(cacheKey);
        
        // 重新查询并缓存
        LogisticsInfo logisticsInfo = logisticsInfoMapper.selectByOrderId(orderId);
        if (logisticsInfo != null) {
            cacheLogisticsInfo(logisticsInfo);
            log.info("刷新物流缓存成功: orderId={}", orderId);
        }
    }
}
