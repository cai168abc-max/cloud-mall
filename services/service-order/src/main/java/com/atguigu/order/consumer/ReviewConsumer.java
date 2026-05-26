package com.atguigu.order.consumer;

import com.atguigu.common.mq.ReviewMessage;
import com.atguigu.order.service.OrderReviewService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 评价消息消费者
 * 用于异步更新商品评价统计
 */
@Component
@RocketMQMessageListener(
    topic = "review-topic",
    consumerGroup = "review-consumer-group"
)
@RequiredArgsConstructor
public class ReviewConsumer implements RocketMQListener<ReviewMessage> {

    private static final Logger log = LoggerFactory.getLogger(ReviewConsumer.class);

    private final OrderReviewService orderReviewService;

    @Override
    public void onMessage(final ReviewMessage message) {
        log.info("收到评价消息: reviewId={}, productId={}, action={}",
                message.getReviewId(), message.getProductId(), message.getAction());

        try {
            // 更新商品评价缓存
            orderReviewService.updateReviewCache(message.getProductId());

            log.info("评价缓存更新成功: productId={}", message.getProductId());

        } catch (Exception e) {
            log.error("处理评价消息失败: reviewId={}, productId={}",
                    message.getReviewId(), message.getProductId(), e);
            // 消费失败会触发RocketMQ的重试机制
            throw new RuntimeException("处理评价消息失败", e);
        }
    }
}
