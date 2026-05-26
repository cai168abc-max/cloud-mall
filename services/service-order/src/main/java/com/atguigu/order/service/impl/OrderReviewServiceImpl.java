package com.atguigu.order.service.impl;

import com.atguigu.common.cache.CacheKeyConstants;
import com.atguigu.common.enums.OrderStatus;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.common.mq.ReviewMessage;
import com.atguigu.order.bean.Order;
import com.atguigu.order.bean.OrderItem;
import com.atguigu.order.bean.OrderReview;
import com.atguigu.order.mapper.OrderItemMapper;
import com.atguigu.order.mapper.OrderMapper;
import com.atguigu.order.mapper.OrderReviewMapper;
import com.atguigu.order.service.OrderReviewService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 订单评价服务实现类
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class OrderReviewServiceImpl implements OrderReviewService {

    private static final Logger log = LoggerFactory.getLogger(OrderReviewServiceImpl.class);

    // 评价相关常量
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_IMAGE_COUNT = 9;
    private static final long CACHE_EXPIRE_SECONDS = 3600; // 缓存过期时间1小时

    // 分布式锁等待时间和持有时间
    private static final long LOCK_WAIT_TIME = 3;
    private static final long LOCK_LEASE_TIME = 5;

    private final OrderReviewMapper orderReviewMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderReview createReview(final Long orderId, final Long userId, final Integer rating, 
                                      final String content, final List<String> images, final Boolean anonymous) {
        // 1. 参数校验
        validateReviewParams(rating, content, images);

        // 2. 获取分布式锁防止重复评价
        String lockKey = CacheKeyConstants.orderReviewLock(orderId);
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // 尝试获取分布式锁
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取评价锁失败，可能正在处理中: orderId={}", orderId);
                // 兜底方案：检查数据库是否已存在评价
                if (orderReviewMapper.existsByOrderId(orderId)) {
                    throw new BusinessException("该订单已评价，请勿重复提交");
                }
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            // 3. 订单校验
            Order order = validateOrder(orderId, userId);

            // 4. 获取订单商品信息（取第一个商品）
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
            if (orderItems == null || orderItems.isEmpty()) {
                throw new BusinessException("订单商品信息不存在");
            }
            OrderItem orderItem = orderItems.get(0);

            // 5. 检查是否已评价（双重校验）
            if (orderReviewMapper.existsByOrderId(orderId)) {
                throw new BusinessException("该订单已评价，请勿重复提交");
            }

            // 6. 创建评价
            OrderReview review = OrderReview.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .merchantId(order.getMerchantId())
                    .productId(orderItem.getProductId())
                    .rating(rating)
                    .content(content)
                    .images(images != null ? toJsonString(images) : null)
                    .anonymous(anonymous != null && anonymous ? 1 : 0)
                    .status(1)
                    .build();

            // 7. 保存评价
            int inserted = orderReviewMapper.insertReview(review);
            if (inserted <= 0) {
                throw new BusinessException("评价保存失败");
            }

            // 8. 更新订单评价状态
            orderMapper.updateReviewed(orderId, 1);

            // 9. 发送MQ消息异步更新统计（事务提交后执行）
            final OrderReview reviewRef = review;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendReviewMessage(reviewRef, ReviewMessage.ACTION_CREATE);
                }
            });

            log.info("评价创建成功: reviewId={}, orderId={}, productId={}, rating={}",
                    review.getId(), orderId, review.getProductId(), rating);

            return review;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统繁忙，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建评价失败: orderId={}, userId={}", orderId, userId, e);
            // 检查是否是唯一约束冲突（兜底方案）
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                throw new BusinessException("该订单已评价，请勿重复提交");
            }
            throw new BusinessException("评价创建失败: " + e.getMessage());
        } finally {
            // 释放锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public OrderReview getReviewById(final Long reviewId) {
        return orderReviewMapper.selectById(reviewId);
    }

    @Override
    public OrderReview getReviewByOrderId(final Long orderId) {
        return orderReviewMapper.selectByOrderId(orderId);
    }

    @Override
    public IPage<OrderReview> listReviewsByUserId(final Long userId, final int pageNum, final int pageSize) {
        Page<OrderReview> page = new Page<>(pageNum, pageSize);
        return orderReviewMapper.selectByUserId(page, userId);
    }

    @Override
    public IPage<OrderReview> listReviewsByProductId(Long productId, int pageNum, int pageSize) {
        Page<OrderReview> page = new Page<>(pageNum, pageSize);
        return orderReviewMapper.selectByProductId(page, productId);
    }

    @Override
    public IPage<OrderReview> listReviewsByMerchantId(Long merchantId, int pageNum, int pageSize) {
        Page<OrderReview> page = new Page<>(pageNum, pageSize);
        return orderReviewMapper.selectByMerchantId(page, merchantId);
    }

    @Override
    public Map<String, Object> getProductReviewStats(final Long productId) {
        Map<String, Object> stats = new HashMap<>();

        // 优先从缓存获取
        Long count = getProductReviewCount(productId);
        Double avgRating = getProductAvgRating(productId);

        stats.put("productId", productId);
        stats.put("reviewCount", count);
        stats.put("avgRating", Math.round(avgRating * 100) / 100.0); // 保留两位小数

        // 获取各评分数量
        List<OrderReviewMapper.RatingCount> ratingCounts = orderReviewMapper.countGroupByRating(productId);
        Map<Integer, Long> ratingDistribution = new HashMap<>();
        for (int i = MIN_RATING; i <= MAX_RATING; i++) {
            ratingDistribution.put(i, 0L);
        }
        for (OrderReviewMapper.RatingCount rc : ratingCounts) {
            ratingDistribution.put(rc.getRating(), rc.getCount());
        }
        stats.put("ratingDistribution", ratingDistribution);

        return stats;
    }

    @Override
    public Long getProductReviewCount(final Long productId) {
        String cacheKey = CacheKeyConstants.productReviewCount(productId);

        // 从缓存获取
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return Long.parseLong(cached.toString());
            } catch (NumberFormatException e) {
                log.warn("缓存评价数量格式错误: productId={}, value={}", productId, cached);
            }
        }

        // 从数据库查询
        long count = orderReviewMapper.countByProductId(productId);

        // 写入缓存
        redisTemplate.opsForValue().set(cacheKey, count, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return count;
    }

    @Override
    public Double getProductAvgRating(final Long productId) {
        String cacheKey = CacheKeyConstants.productReviewAvg(productId);

        // 从缓存获取
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return Double.parseDouble(cached.toString());
            } catch (NumberFormatException e) {
                log.warn("缓存平均评分格式错误: productId={}, value={}", productId, cached);
            }
        }

        // 从数据库查询
        Double avgRating = orderReviewMapper.avgRatingByProductId(productId);
        if (avgRating == null) {
            avgRating = 0.0;
        }

        // 写入缓存
        redisTemplate.opsForValue().set(cacheKey, avgRating, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return avgRating;
    }

    @Override
    public void updateReviewCache(final Long productId) {
        // 更新评价数量缓存
        long count = orderReviewMapper.countByProductId(productId);
        String countKey = CacheKeyConstants.productReviewCount(productId);
        redisTemplate.opsForValue().set(countKey, count, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 更新平均评分缓存
        Double avgRating = orderReviewMapper.avgRatingByProductId(productId);
        if (avgRating == null) {
            avgRating = 0.0;
        }
        String avgKey = CacheKeyConstants.productReviewAvg(productId);
        redisTemplate.opsForValue().set(avgKey, avgRating, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        log.info("更新评价缓存: productId={}, count={}, avgRating={}", productId, count, avgRating);
    }

    @Override
    public void syncReviewStatsToDb(Long productId) {
        // 从Redis获取缓存的统计数据
        Long cachedCount = getProductReviewCount(productId);
        Double cachedAvgRating = getProductAvgRating(productId);

        // 从数据库获取实际统计数据
        long dbCount = orderReviewMapper.countByProductId(productId);
        Double dbAvgRating = orderReviewMapper.avgRatingByProductId(productId);
        if (dbAvgRating == null) {
            dbAvgRating = 0.0;
        }

        // 比较并同步（如果差异较大，更新缓存）
        if (!cachedCount.equals(dbCount) || Math.abs(cachedAvgRating - dbAvgRating) > 0.01) {
            log.info("同步评价统计: productId={}, 缓存count={}, DBcount={}, 缓存avg={}, DBavg={}",
                    productId, cachedCount, dbCount, cachedAvgRating, dbAvgRating);
            updateReviewCache(productId);
        }
    }

    @Override
    public void syncAllReviewStats() {
        log.info("开始同步所有商品评价统计...");
        // 这里可以优化为批量查询所有有评价的商品ID
        // 当前实现为简单版本，实际生产环境应该使用更高效的批量处理方式
        log.info("评价统计同步完成");
    }

    @Override
    public boolean hasReviewed(final Long orderId) {
        return orderReviewMapper.existsByOrderId(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateReviewStatus(Long reviewId, Integer status, Long operator) {
        OrderReview review = orderReviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }

        int updated = orderReviewMapper.updateStatus(reviewId, status);
        if (updated > 0) {
            final OrderReview reviewRef = review;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendReviewMessage(reviewRef, ReviewMessage.ACTION_UPDATE);
                }
            });
            log.info("评价状态更新: reviewId={}, status={}, operator={}", reviewId, status, operator);
            return true;
        }
        return false;
    }

    /**
     * 校验评价参数
     */
    private void validateReviewParams(Integer rating, String content, List<String> images) {
        // 评分校验
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new BusinessException("评分无效，评分必须在" + MIN_RATING + "-" + MAX_RATING + "之间");
        }

        // 内容长度校验
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("评价内容过长，最多" + MAX_CONTENT_LENGTH + "个字符");
        }

        // 图片数量校验
        if (images != null && images.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException("图片数量过多，最多" + MAX_IMAGE_COUNT + "张");
        }
    }

    /**
     * 校验订单
     */
    private Order validateOrder(final Long orderId, final Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 订单所有权校验
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权评价此订单");
        }

        // 订单状态校验：只有已完成的订单才能评价
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException("订单未完成，无法评价");
        }

        // 检查订单是否已评价
        if (order.getReviewed() != null && order.getReviewed() == 1) {
            throw new BusinessException("该订单已评价");
        }

        return order;
    }

    /**
     * 发送评价消息
     */
    private void sendReviewMessage(final OrderReview review, final String action) {
        try {
            ReviewMessage message = ReviewMessage.builder()
                    .reviewId(review.getId())
                    .orderId(review.getOrderId())
                    .productId(review.getProductId())
                    .userId(review.getUserId())
                    .merchantId(review.getMerchantId())
                    .rating(review.getRating())
                    .action(action)
                    .timestamp(System.currentTimeMillis())
                    .build();

            rocketMQTemplate.asyncSend("review-topic", message, new SendCallback() {
                @Override
                public void onSuccess(final SendResult sendResult) {
                    log.info("评价消息发送成功: reviewId={}, action={}", review.getId(), action);
                }

                @Override
                public void onException(final Throwable e) {
                    log.error("评价消息发送失败: reviewId={}, action={}", review.getId(), action, e);
                }
            });
        } catch (Exception e) {
            log.error("发送评价消息异常: reviewId={}", review.getId(), e);
        }
    }

    /**
     * 列表转JSON字符串
     */
    private String toJsonString(final List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            return null;
        }
    }
}
