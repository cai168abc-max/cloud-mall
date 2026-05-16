package com.atguigu.user.consumer;

import com.atguigu.common.mq.OrderNotifyMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
    topic = "order-notify-topic",
    consumerGroup = "order-notify-consumer-group"
)
public class OrderNotifyConsumer implements RocketMQListener<OrderNotifyMessage> {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifyConsumer.class);

    @Override
    public void onMessage(OrderNotifyMessage message) {
        log.info("收到订单通知消息: orderId={}, userId={}, status={}, message={}",
            message.getOrderId(), message.getUserId(), message.getStatus(), message.getMessage());

        switch (message.getStatus()) {
            case "CREATED":
                handleOrderCreated(message);
                break;
            case "PAID":
                handleOrderPaid(message);
                break;
            case "SHIPPED":
                handleOrderShipped(message);
                break;
            case "COMPLETED":
                handleOrderCompleted(message);
                break;
            case "CANCELLED":
                handleOrderCancelled(message);
                break;
            default:
                log.warn("未知的订单状态: {}", message.getStatus());
        }
    }

    private void handleOrderCreated(OrderNotifyMessage message) {
        log.info("【订单创建通知】订单 {} 已创建，等待支付", message.getOrderId());
    }

    private void handleOrderPaid(OrderNotifyMessage message) {
        log.info("【订单支付通知】订单 {} 已支付", message.getOrderId());
    }

    private void handleOrderShipped(OrderNotifyMessage message) {
        log.info("【订单发货通知】订单 {} 已发货", message.getOrderId());
    }

    private void handleOrderCompleted(OrderNotifyMessage message) {
        log.info("【订单完成通知】订单 {} 已完成", message.getOrderId());
    }

    private void handleOrderCancelled(OrderNotifyMessage message) {
        log.info("【订单取消通知】订单 {} 已取消", message.getOrderId());
    }
}
