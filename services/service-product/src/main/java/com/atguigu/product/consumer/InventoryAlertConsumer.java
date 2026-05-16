package com.atguigu.product.consumer;

import com.atguigu.common.mq.InventoryAlertMessage;
import com.atguigu.product.service.InventoryAlertService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 库存预警消息消费者
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>接收库存预警消息</li>
 *   <li>查询商家联系方式</li>
 *   <li>发送通知（站内信/短信/邮件）</li>
 *   <li>更新预警记录状态</li>
 *   <li>设置Redis去重标记</li>
 * </ul>
 * 
 * <p>异常处理：</p>
 * <ul>
 *   <li>商家联系方式不存在：记录日志</li>
 *   <li>通知发送失败：重试3次，仍失败则标记为FAILED</li>
 * </ul>
 */
@Component
@RocketMQMessageListener(
    topic = "inventory-alert-topic",
    consumerGroup = "inventory-alert-consumer-group"
)
@RequiredArgsConstructor
public class InventoryAlertConsumer implements RocketMQListener<InventoryAlertMessage> {

    private static final Logger log = LoggerFactory.getLogger(InventoryAlertConsumer.class);

    private final InventoryAlertService inventoryAlertService;

    @Override
    public void onMessage(InventoryAlertMessage message) {
        log.info("收到库存预警消息: alertLogId={}, productId={}, merchantId={}, stock={}, threshold={}",
                message.getAlertLogId(), message.getProductId(), message.getMerchantId(),
                message.getStock(), message.getThreshold());

        try {
            // 处理预警通知
            inventoryAlertService.processAlertNotification(message.getAlertLogId());

            log.info("库存预警消息处理成功: alertLogId={}, productId={}",
                    message.getAlertLogId(), message.getProductId());

        } catch (Exception e) {
            log.error("处理库存预警消息失败: alertLogId={}, productId={}",
                    message.getAlertLogId(), message.getProductId(), e);
            // 消费失败会触发RocketMQ的重试机制
            throw new RuntimeException("处理库存预警消息失败", e);
        }
    }
}
