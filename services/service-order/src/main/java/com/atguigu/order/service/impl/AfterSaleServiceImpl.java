package com.atguigu.order.service.impl;

import com.atguigu.common.enums.OrderStatus;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.common.mq.OrderNotifyMessage;
import com.atguigu.order.bean.AfterSaleTicket;
import com.atguigu.order.bean.Order;
import com.atguigu.order.bean.OrderItem;
import com.atguigu.order.bean.VirtualAccountLog;
import com.atguigu.order.feign.ProductFeign;
import com.atguigu.order.mapper.AfterSaleTicketMapper;
import com.atguigu.order.mapper.OrderItemMapper;
import com.atguigu.order.mapper.OrderMapper;
import com.atguigu.order.service.AfterSaleService;
import com.atguigu.order.service.VirtualAccountService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 售后服务实现类
 * 核心特性：
 * 1. 售后期限校验：订单完成后7天内
 * 2. 分布式锁：防止重复申请
 * 3. MQ消息：售后状态通知
 * 4. 退款兜底：退款失败时提供手动退款接口
 */
@Service
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class AfterSaleServiceImpl implements AfterSaleService {

    private static final Logger log = LoggerFactory.getLogger(AfterSaleServiceImpl.class);

    private final AfterSaleTicketMapper ticketMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final VirtualAccountService virtualAccountService;
    private final ProductFeign productFeign;
    private final RocketMQTemplate rocketMQTemplate;
    private final RedissonClient redissonClient;
    private final TransactionTemplate requiresNewTemplate;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AfterSaleServiceImpl(final AfterSaleTicketMapper ticketMapper,
                                final OrderMapper orderMapper,
                                final OrderItemMapper orderItemMapper,
                                final VirtualAccountService virtualAccountService,
                                final ProductFeign productFeign,
                                final RocketMQTemplate rocketMQTemplate,
                                final RedissonClient redissonClient,
                                final PlatformTransactionManager transactionManager) {
        this.ticketMapper = ticketMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.virtualAccountService = virtualAccountService;
        this.productFeign = productFeign;
        this.rocketMQTemplate = rocketMQTemplate;
        this.redissonClient = redissonClient;
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 售后期限（天）
     */
    private static final int AFTER_SALE_DEADLINE_DAYS = 7;

    /**
     * 分布式锁等待时间（秒）
     */
    private static final long LOCK_WAIT_SECONDS = 3;

    /**
     * 分布式锁持有时间（秒）
     */
    private static final long LOCK_LEASE_SECONDS = 10;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleTicket applyAfterSale(final Long orderId, final String type, final String reason, 
                                           final String description, final String images, final Long userId) {
        if (orderId == null) {
            throw new BusinessException("订单ID不能为空");
        }
        if (type == null || type.isEmpty()) {
            throw new BusinessException("售后类型不能为空");
        }
        if (reason == null || reason.isEmpty()) {
            throw new BusinessException("申请原因不能为空");
        }
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }

        if (!AfterSaleTicket.TYPE_REFUND.equals(type)
            && !AfterSaleTicket.TYPE_RETURN.equals(type)
            && !AfterSaleTicket.TYPE_EXCHANGE.equals(type)) {
            throw new BusinessException("无效的售后类型");
        }

        String lockKey = "after-sale:apply:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                AfterSaleTicket result = doApplyAfterSale(orderId, type, reason, description, images, userId);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
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

    /**
     * 执行申请售后
     */
    private AfterSaleTicket doApplyAfterSale(final Long orderId, final String type, final String reason, final String description, final String images, final Long userId) {
        // 1. 查询订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 校验订单所有权
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此订单");
        }

        // 3. 校验订单状态（必须是已完成状态）
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException("订单未完成，无法申请售后");
        }

        // 4. 校验售后期限（订单完成后7天内）
        LocalDateTime completeTime = order.getCompleteTime();
        if (completeTime == null) {
            throw new BusinessException("订单完成时间异常");
        }
        long daysBetween = ChronoUnit.DAYS.between(completeTime, LocalDateTime.now());
        if (daysBetween > AFTER_SALE_DEADLINE_DAYS) {
            throw new BusinessException("已超过售后期限（" + AFTER_SALE_DEADLINE_DAYS + "天内）");
        }

        // 5. 检查是否已有进行中的售后
        int processingCount = ticketMapper.countProcessingByOrderId(orderId);
        if (processingCount > 0) {
            throw new BusinessException("已有售后申请正在处理中");
        }

        // 6. 创建售后工单
        AfterSaleTicket ticket = AfterSaleTicket.builder()
                .orderId(orderId)
                .userId(userId)
                .merchantId(order.getMerchantId())
                .type(type)
                .reason(reason)
                .description(description)
                .images(images)
                .status(AfterSaleTicket.STATUS_PENDING)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        ticketMapper.insertTicket(ticket);
        log.info("创建售后工单成功, ticketId={}, orderId={}, userId={}, type={}", 
                ticket.getId(), orderId, userId, type);

        // 7. 发送售后申请消息到MQ（事务提交后执行）
        final AfterSaleTicket ticketRef = ticket;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendAfterSaleMessage(ticketRef, "APPLY", "用户申请售后");
            }
        });

        return ticket;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approveAfterSale(final Long ticketId, final Long merchantId, final BigDecimal refundAmount) {
        if (ticketId == null) {
            throw new BusinessException("工单ID不能为空");
        }
        if (merchantId == null) {
            throw new BusinessException("商家ID不能为空");
        }

        String lockKey = "after-sale:process:" + ticketId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                boolean result = doApproveAfterSale(ticketId, merchantId, refundAmount);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
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

    private boolean doApproveAfterSale(final Long ticketId, final Long merchantId, final BigDecimal refundAmount) {
        final BigDecimal[] refundAmountHolder = new BigDecimal[1];
        final AfterSaleTicket[] ticketHolder = new AfterSaleTicket[1];
        final Order[] orderHolder = new Order[1];

        requiresNewTemplate.execute(status -> {
            AfterSaleTicket ticket = ticketMapper.selectById(ticketId);
            if (ticket == null) {
                throw new BusinessException("工单不存在");
            }
            if (!AfterSaleTicket.STATUS_PENDING.equals(ticket.getStatus())) {
                throw new BusinessException("工单已处理，请勿重复操作");
            }
            if (!ticket.getMerchantId().equals(merchantId)) {
                throw new BusinessException("无权操作此工单");
            }

            Order order = orderMapper.selectById(ticket.getOrderId());
            if (order == null) {
                throw new BusinessException("订单不存在");
            }

            BigDecimal actualRefundAmount = refundAmount;
            if (actualRefundAmount == null || actualRefundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                actualRefundAmount = order.getPayAmount();
            }
            if (actualRefundAmount.compareTo(order.getPayAmount()) > 0) {
                throw new BusinessException("退款金额不能超过订单支付金额");
            }

            ticketMapper.updateStatusToApproved(ticketId, AfterSaleTicket.STATUS_APPROVED, actualRefundAmount);

            ticketHolder[0] = ticket;
            ticketHolder[0].setStatus(AfterSaleTicket.STATUS_APPROVED);
            ticketHolder[0].setRefundAmount(actualRefundAmount);
            refundAmountHolder[0] = actualRefundAmount;
            orderHolder[0] = order;

            return null;
        });

        AfterSaleTicket ticket = ticketHolder[0];
        BigDecimal finalRefundAmount = refundAmountHolder[0];

        VirtualAccountLog refundLog;
        try {
            refundLog = virtualAccountService.refund(
                    ticket.getUserId(),
                    finalRefundAmount,
                    ticket.getOrderId(),
                    null);
        } catch (Exception e) {
            log.error("售后退款失败, ticketId={}, orderId={}, error={}",
                    ticketId, ticket.getOrderId(), e.getMessage());
            requiresNewTemplate.execute(status -> {
                ticketMapper.updateStatus(ticketId, AfterSaleTicket.STATUS_PROCESSING);
                return null;
            });
            ticket.setStatus(AfterSaleTicket.STATUS_PROCESSING);
            sendAfterSaleMessage(ticket, "PROCESSING", "退款失败，需人工处理: " + e.getMessage());
            throw new BusinessException("退款失败，已转为人工处理，请稍后重试或使用手动退款功能");
        }

        List<OrderItem> orderItems = orderItemMapper.selectByOrderId(ticket.getOrderId());
        if (orderItems != null && !orderItems.isEmpty()) {
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

        try {
            requiresNewTemplate.execute(status -> {
                ticketMapper.updateStatusToCompleted(ticketId, AfterSaleTicket.STATUS_COMPLETED, refundLog.getTransactionNo());
                orderMapper.updateStatus(ticket.getOrderId(), OrderStatus.REFUNDED.name());
                return null;
            });
        } catch (Exception e) {
            log.error("退款成功但状态更新失败, ticketId={}, orderId={}, transactionNo={}, 需人工确认",
                    ticketId, ticket.getOrderId(), refundLog.getTransactionNo(), e);
            ticket.setStatus(AfterSaleTicket.STATUS_APPROVED);
            sendAfterSaleMessage(ticket, "APPROVED", "退款已成功但状态更新异常，需人工确认: " + e.getMessage());
            throw new BusinessException("退款已处理，状态更新中，请稍后查看");
        }

        ticket.setStatus(AfterSaleTicket.STATUS_COMPLETED);
        ticket.setRefundTransactionNo(refundLog.getTransactionNo());
        log.info("售后退款成功, ticketId={}, orderId={}, refundAmount={}, transactionNo={}",
                ticketId, ticket.getOrderId(), finalRefundAmount, refundLog.getTransactionNo());
        sendAfterSaleMessage(ticket, "COMPLETED", "售后退款成功");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rejectAfterSale(final Long ticketId, final Long merchantId, final String rejectReason) {
        if (ticketId == null) {
            throw new BusinessException("工单ID不能为空");
        }
        if (merchantId == null) {
            throw new BusinessException("商家ID不能为空");
        }
        if (rejectReason == null || rejectReason.isEmpty()) {
            throw new BusinessException("拒绝原因不能为空");
        }

        String lockKey = "after-sale:process:" + ticketId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                boolean result = doRejectAfterSale(ticketId, merchantId, rejectReason);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
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

    /**
     * 执行拒绝售后
     */
    private boolean doRejectAfterSale(final Long ticketId, final Long merchantId, final String rejectReason) {
        // 1. 查询工单
        AfterSaleTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }

        // 2. 校验工单状态
        if (!AfterSaleTicket.STATUS_PENDING.equals(ticket.getStatus())) {
            throw new BusinessException("工单已处理，请勿重复操作");
        }

        // 3. 校验商家权限
        if (!ticket.getMerchantId().equals(merchantId)) {
            throw new BusinessException("无权操作此工单");
        }

        // 4. 更新工单状态为已拒绝
        int updated = ticketMapper.updateStatusToRejected(ticketId, AfterSaleTicket.STATUS_REJECTED, rejectReason);
        if (updated <= 0) {
            throw new BusinessException("操作失败，请重试");
        }

        log.info("售后工单已拒绝, ticketId={}, orderId={}, rejectReason={}", 
                ticketId, ticket.getOrderId(), rejectReason);

        // 5. 发送售后拒绝消息到MQ（事务提交后执行）
        ticket.setStatus(AfterSaleTicket.STATUS_REJECTED);
        ticket.setRejectReason(rejectReason);
        final AfterSaleTicket ticketRef = ticket;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendAfterSaleMessage(ticketRef, "REJECTED", "商家拒绝售后: " + rejectReason);
            }
        });

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean manualRefund(final Long ticketId, final Long merchantId) {
        if (ticketId == null) {
            throw new BusinessException("工单ID不能为空");
        }
        if (merchantId == null) {
            throw new BusinessException("商家ID不能为空");
        }

        String lockKey = "after-sale:process:" + ticketId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                boolean result = doManualRefund(ticketId, merchantId);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
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

    /**
     * 执行手动退款（兜底方案）
     */
    private boolean doManualRefund(final Long ticketId, final Long merchantId) {
        // 1. 查询工单
        AfterSaleTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }

        // 2. 校验工单状态（只有处理中状态的工单才能手动退款）
        if (!AfterSaleTicket.STATUS_PROCESSING.equals(ticket.getStatus())
            && !AfterSaleTicket.STATUS_APPROVED.equals(ticket.getStatus())) {
            throw new BusinessException("工单状态异常，无法手动退款");
        }

        // 3. 校验商家权限
        if (!ticket.getMerchantId().equals(merchantId)) {
            throw new BusinessException("无权操作此工单");
        }

        // 4. 获取退款金额
        BigDecimal refundAmount = ticket.getRefundAmount();
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            Order order = orderMapper.selectById(ticket.getOrderId());
            if (order == null) {
                throw new BusinessException("订单不存在");
            }
            refundAmount = order.getPayAmount();
        }

        // 5. 执行退款
        try {
            VirtualAccountLog refundLog = virtualAccountService.refund(
                    ticket.getUserId(), 
                    refundAmount, 
                    ticket.getOrderId(), 
                    null);

            // 退款成功，更新工单状态为已完成
            ticketMapper.updateStatusToCompleted(ticketId, AfterSaleTicket.STATUS_COMPLETED, refundLog.getTransactionNo());

            // 更新订单状态为已退款
            orderMapper.updateStatus(ticket.getOrderId(), OrderStatus.REFUNDED.name());

            log.info("手动退款成功, ticketId={}, orderId={}, refundAmount={}, transactionNo={}", 
                    ticketId, ticket.getOrderId(), refundAmount, refundLog.getTransactionNo());

            // 发送售后完成消息到MQ（事务提交后执行）
            ticket.setStatus(AfterSaleTicket.STATUS_COMPLETED);
            ticket.setRefundTransactionNo(refundLog.getTransactionNo());
            final AfterSaleTicket ticketRef = ticket;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAfterSaleMessage(ticketRef, "COMPLETED", "手动退款成功");
                }
            });

            return true;

        } catch (BusinessException e) {
            log.error("手动退款失败, ticketId={}, orderId={}, error={}", 
                    ticketId, ticket.getOrderId(), e.getMessage());
            throw new BusinessException("退款失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelAfterSale(final Long ticketId, final Long userId) {
        if (ticketId == null) {
            throw new BusinessException("工单ID不能为空");
        }
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }

        String lockKey = "after-sale:process:" + ticketId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                boolean result = doCancelAfterSale(ticketId, userId);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
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

    private boolean doCancelAfterSale(final Long ticketId, final Long userId) {
        AfterSaleTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        if (!ticket.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此工单");
        }
        if (!AfterSaleTicket.STATUS_PENDING.equals(ticket.getStatus())) {
            throw new BusinessException("工单已处理，无法取消");
        }

        int updated = ticketMapper.updateStatus(ticketId, AfterSaleTicket.STATUS_CANCELLED);
        if (updated <= 0) {
            throw new BusinessException("操作失败，请重试");
        }

        log.info("售后工单已取消, ticketId={}, orderId={}, userId={}", 
                ticketId, ticket.getOrderId(), userId);

        ticket.setStatus(AfterSaleTicket.STATUS_CANCELLED);
        final AfterSaleTicket ticketRef = ticket;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendAfterSaleMessage(ticketRef, "CANCELLED", "用户取消售后");
            }
        });

        return true;
    }

    @Override
    public AfterSaleTicket getTicketById(final Long ticketId) {
        if (ticketId == null) {
            return null;
        }
        return ticketMapper.selectById(ticketId);
    }

    @Override
    public AfterSaleTicket getTicketByOrderId(final Long orderId) {
        if (orderId == null) {
            return null;
        }
        return ticketMapper.selectByOrderId(orderId);
    }

    @Override
    public List<AfterSaleTicket> listTicketsByUserId(final Long userId) {
        if (userId == null) {
            return List.of();
        }
        return ticketMapper.selectByUserId(userId);
    }

    @Override
    public IPage<AfterSaleTicket> listTicketsByUserId(final Long userId, final int pageNum, final int pageSize) {
        if (userId == null) {
            return new Page<>(pageNum, pageSize);
        }
        Page<AfterSaleTicket> page = new Page<>(pageNum, pageSize);
        return ticketMapper.selectPage(page, 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AfterSaleTicket>()
                        .eq(AfterSaleTicket::getUserId, userId)
                        .orderByDesc(AfterSaleTicket::getCreateTime));
    }

    @Override
    public List<AfterSaleTicket> listTicketsByMerchantId(final Long merchantId) {
        if (merchantId == null) {
            return List.of();
        }
        return ticketMapper.selectByMerchantId(merchantId);
    }

    @Override
    public IPage<AfterSaleTicket> listTicketsByMerchantId(final Long merchantId, final int pageNum, final int pageSize) {
        if (merchantId == null) {
            return new Page<>(pageNum, pageSize);
        }
        Page<AfterSaleTicket> page = new Page<>(pageNum, pageSize);
        return ticketMapper.selectPage(page, 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AfterSaleTicket>()
                        .eq(AfterSaleTicket::getMerchantId, merchantId)
                        .orderByDesc(AfterSaleTicket::getCreateTime));
    }

    @Override
    public List<AfterSaleTicket> listTicketsByMerchantIdAndStatus(final Long merchantId, final String status) {
        if (merchantId == null) {
            return List.of();
        }
        return ticketMapper.selectByMerchantIdAndStatus(merchantId, status);
    }

    /**
     * 发送售后消息到MQ
     */
    private void sendAfterSaleMessage(final AfterSaleTicket ticket, final String action, final String message) {
        try {
            OrderNotifyMessage notifyMessage = OrderNotifyMessage.builder()
                    .orderId(ticket.getOrderId())
                    .userId(ticket.getUserId())
                    .status(ticket.getStatus())
                    .message("售后工单[" + ticket.getId() + "] " + action + ": " + message)
                    .build();

            rocketMQTemplate.asyncSend("after-sale-notify-topic", notifyMessage, 
                    new SendCallback() {
                        @Override
                        public void onSuccess(final SendResult sendResult) {
                            log.info("售后通知消息发送成功, ticketId={}, action={}", ticket.getId(), action);
                        }

                        @Override
                        public void onException(final Throwable e) {
                            log.error("售后通知消息发送失败, ticketId={}, action={}", ticket.getId(), action, e);
                        }
                    });
        } catch (Exception e) {
            log.error("发送售后通知消息异常, ticketId={}", ticket.getId(), e);
        }
    }
}
