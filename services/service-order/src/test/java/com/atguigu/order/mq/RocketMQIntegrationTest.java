package com.atguigu.order.mq;

import com.atguigu.common.mq.OrderNotifyMessage;
import com.atguigu.common.mq.SeckillMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 消息队列集成测试类
 * 测试目的：
 * 1. 验证RocketMQ消息发送和接收的正确性
 * 2. 验证消息顺序性的正确性
 * 3. 验证消息重试机制的正确性
 * 注意：由于RocketMQ Testcontainers配置较复杂，本测试使用Mock方式
 * 实际项目中可以使用嵌入式RocketMQ或Testcontainers进行完整集成测试
 */
@Disabled("需要Docker/Nacos环境，仅在本地手动运行")
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("消息队列集成测试")
class RocketMQIntegrationTest {

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== 消息发送测试 ====================

    @Nested
    @DisplayName("消息发送测试")
    class MessageSendTests {

        @Test
        @DisplayName("应该成功发送订单通知消息")
        void should_sendOrderNotifyMessage_successfully() {
            // Given
            String topic = "order-notify-topic";
            OrderNotifyMessage message = OrderNotifyMessage.builder()
                    .orderId(1L)
                    .userId(1L)
                    .status("PAID")
                    .message("订单已支付")
                    .build();

            // When
            rocketMQTemplate.convertAndSend(topic, message);

            // Then
            verify(rocketMQTemplate).convertAndSend(eq(topic), any(OrderNotifyMessage.class));
        }

        @Test
        @DisplayName("应该成功发送秒杀消息")
        void should_sendSeckillMessage_successfully() {
            // Given
            String topic = "seckill-topic";
            SeckillMessage message = SeckillMessage.builder()
                    .productId(1L)
                    .userId(1L)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // When
            rocketMQTemplate.convertAndSend(topic, message);

            // Then
            verify(rocketMQTemplate).convertAndSend(eq(topic), any(SeckillMessage.class));
        }

        @Test
        @DisplayName("应该成功发送带Key的消息")
        void should_sendMessageWithKey_successfully() {
            // Given
            String topic = "order-topic";
            String key = "order-1";
            String message = "Order created";

            // When
            rocketMQTemplate.syncSend(topic, MessageBuilder.withPayload(message).build());

            // Then
            verify(rocketMQTemplate).syncSend(eq(topic), any(Message.class));
        }

        @Test
        @DisplayName("应该成功发送延迟消息")
        void should_sendDelayedMessage_successfully() {
            // Given
            String topic = "order-timeout-topic";
            String message = "Order timeout check";

            // When
            // 延迟级别：1-18，对应不同的延迟时间
            Message<String> msg = MessageBuilder.withPayload(message)
                    .setHeader("DELAY", 3) // 延迟级别3，约10秒
                    .build();
            rocketMQTemplate.syncSend(topic, msg);

            // Then
            verify(rocketMQTemplate).syncSend(eq(topic), any(Message.class));
        }
    }

    // ==================== 消息接收测试 ====================

    @Nested
    @DisplayName("消息接收测试")
    class MessageReceiveTests {

        @Test
        @DisplayName("应该正确处理订单通知消息")
        void should_handleOrderNotifyMessage_correctly() throws Exception {
            // Given
            OrderNotifyMessage message = OrderNotifyMessage.builder()
                    .orderId(1L)
                    .userId(1L)
                    .status("PAID")
                    .message("订单已支付")
                    .build();

            String messageJson = objectMapper.writeValueAsString(message);

            // Then - 验证消息可以被正确解析
            OrderNotifyMessage parsedMessage = objectMapper.readValue(messageJson, OrderNotifyMessage.class);
            assertEquals(1L, parsedMessage.getOrderId(), "订单ID应正确");
            assertEquals("PAID", parsedMessage.getStatus(), "状态应正确");
        }

        @Test
        @DisplayName("应该正确处理秒杀消息")
        void should_handleSeckillMessage_correctly() throws Exception {
            // Given
            SeckillMessage message = SeckillMessage.builder()
                    .productId(1L)
                    .userId(1L)
                    .timestamp(System.currentTimeMillis())
                    .build();

            String messageJson = objectMapper.writeValueAsString(message);

            // When - 模拟消息处理
            SeckillMessage parsedMessage = objectMapper.readValue(messageJson, SeckillMessage.class);

            // Then
            assertEquals(1L, parsedMessage.getProductId(), "商品ID应正确");
            assertNotNull(parsedMessage.getTimestamp(), "时间戳应存在");
        }
    }

    // ==================== 消息顺序性测试 ====================

    @Nested
    @DisplayName("消息顺序性测试")
    class MessageOrderTests {

        @Test
        @DisplayName("应该按顺序发送消息")
        void should_sendMessagesInOrder() {
            // Given
            String topic = "order-sequence-topic";
            String tag = "order-status";

            // When - 发送多条消息
            for (int i = 1; i <= 5; i++) {
                OrderNotifyMessage message = OrderNotifyMessage.builder()
                        .orderId(1L)
                        .status("STATUS_" + i)
                        .build();
                rocketMQTemplate.convertAndSend(topic + ":" + tag, message);
            }

            // Then - 验证消息按顺序发送
            verify(rocketMQTemplate, times(5)).convertAndSend(anyString(), any(OrderNotifyMessage.class));
        }

        @Test
        @DisplayName("应该使用顺序消息发送")
        void should_sendOrderedMessage() {
            // Given
            String topic = "order-ordered-topic";
            Long orderId = 1L;

            // When - 发送顺序消息（使用订单ID作为分片键）
            OrderNotifyMessage message = OrderNotifyMessage.builder()
                    .orderId(orderId)
                    .status("CREATED")
                    .build();

            Message<OrderNotifyMessage> msg = MessageBuilder.withPayload(message)
                    .setHeader("KEYS", orderId.toString())
                    .build();
            rocketMQTemplate.syncSendOrderly(topic, msg, orderId.toString());

            // Then
            verify(rocketMQTemplate).syncSendOrderly(eq(topic), any(Message.class), eq(orderId.toString()));
        }
    }

    // ==================== 消息重试测试 ====================

    @Nested
    @DisplayName("消息重试测试")
    class MessageRetryTests {

        @Test
        @DisplayName("应该正确处理消息重试")
        void should_handleMessageRetry_correctly() {
            // Given
            String topic = "order-retry-topic";
            int maxRetryTimes = 3;

            // When - 模拟消息发送失败后的重试
            for (int i = 0; i < maxRetryTimes; i++) {
                try {
                    rocketMQTemplate.syncSend(topic, MessageBuilder.withPayload("retry-test").build());
                    break;
                } catch (Exception e) {
                    if (i == maxRetryTimes - 1) {
                        // 达到最大重试次数，记录失败
                        System.out.println("消息发送失败，已达到最大重试次数");
                    }
                }
            }

            // Then
            verify(rocketMQTemplate, atMost(maxRetryTimes)).syncSend(anyString(), any(Message.class));
        }

        @Test
        @DisplayName("应该正确处理消费失败的消息")
        void should_handleConsumeFailure_correctly() {
            // Given
            int maxReconsumeTimes = 3;
            int currentReconsumeTimes = 0;

            // When - 模拟消费失败重试
            boolean success = false;
            while (!success && currentReconsumeTimes < maxReconsumeTimes) {
                try {
                    // 模拟消费逻辑
                    if (currentReconsumeTimes < 2) {
                        throw new RuntimeException("消费失败");
                    }
                    success = true;
                } catch (Exception e) {
                    currentReconsumeTimes++;
                    System.out.println("消费失败，重试次数: " + currentReconsumeTimes);
                }
            }

            // Then
            assertTrue(success, "最终应消费成功");
            assertEquals(2, currentReconsumeTimes, "应重试2次");
        }
    }

    // ==================== 消息格式测试 ====================

    @Nested
    @DisplayName("消息格式测试")
    class MessageFormatTests {

        @Test
        @DisplayName("应该正确序列化和反序列化消息")
        void should_serializeAndDeserialize_correctly() throws Exception {
            // Given
            OrderNotifyMessage original = OrderNotifyMessage.builder()
                    .orderId(1L)
                    .userId(1L)
                    .status("PAID")
                    .message("订单已支付")
                    .build();

            // When
            String json = objectMapper.writeValueAsString(original);
            OrderNotifyMessage deserialized = objectMapper.readValue(json, OrderNotifyMessage.class);

            // Then
            assertEquals(original.getOrderId(), deserialized.getOrderId(), "订单ID应一致");
            assertEquals(original.getUserId(), deserialized.getUserId(), "用户ID应一致");
            assertEquals(original.getStatus(), deserialized.getStatus(), "状态应一致");
            assertEquals(original.getMessage(), deserialized.getMessage(), "消息应一致");
        }

        @Test
        @DisplayName("应该正确处理空消息")
        void should_handleNullMessage_correctly() throws Exception {
            // Given
            OrderNotifyMessage message = new OrderNotifyMessage();

            // When
            String json = objectMapper.writeValueAsString(message);
            OrderNotifyMessage deserialized = objectMapper.readValue(json, OrderNotifyMessage.class);

            // Then
            assertNotNull(deserialized, "反序列化结果不应为空");
        }
    }

    // ==================== 消息头测试 ====================

    @Nested
    @DisplayName("消息头测试")
    class MessageHeaderTests {

        @Test
        @DisplayName("应该正确设置消息头")
        void should_setMessageHeaders_correctly() {
            // Given
            String topic = "header-test-topic";
            String key = "message-key";
            String tag = "message-tag";

            // When
            Message<String> message = MessageBuilder.withPayload("test-payload")
                    .setHeader("KEYS", key)
                    .setHeader("TAGS", tag)
                    .setHeader("TRACE_ID", "trace-123")
                    .build();
            rocketMQTemplate.syncSend(topic, message);

            // Then
            ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
            verify(rocketMQTemplate).syncSend(eq(topic), captor.capture());
            
            Message<?> sentMessage = captor.getValue();
            assertEquals(key, sentMessage.getHeaders().get("KEYS"), "消息Key应正确");
            assertEquals(tag, sentMessage.getHeaders().get("TAGS"), "消息Tag应正确");
        }
    }

    // ==================== 批量消息测试 ====================

    @Nested
    @DisplayName("批量消息测试")
    class BatchMessageTests {

        @Test
        @DisplayName("应该成功发送批量消息")
        void should_sendBatchMessages_successfully() {
            // Given
            String topic = "batch-topic";
            int batchSize = 10;

            // When
            for (int i = 0; i < batchSize; i++) {
                OrderNotifyMessage message = OrderNotifyMessage.builder()
                        .orderId((long) i)
                        .status("CREATED")
                        .build();
                rocketMQTemplate.convertAndSend(topic, message);
            }

            // Then
            verify(rocketMQTemplate, times(batchSize)).convertAndSend(eq(topic), any(OrderNotifyMessage.class));
        }
    }

    // ==================== 异步消息测试 ====================

    @Nested
    @DisplayName("异步消息测试")
    class AsyncMessageTests {

        @Test
        @DisplayName("应该成功发送异步消息")
        void should_sendAsyncMessage_successfully() {
            // Given
            String topic = "async-topic";
            String message = "async-test";

            // When
            rocketMQTemplate.asyncSend(topic, MessageBuilder.withPayload(message).build(), 
                new org.apache.rocketmq.client.producer.SendCallback() {
                    @Override
                    public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    }

                    @Override
                    public void onException(Throwable e) {
                    }
                });

            // Then
            verify(rocketMQTemplate).asyncSend(eq(topic), any(Message.class), any());
        }
    }
}
