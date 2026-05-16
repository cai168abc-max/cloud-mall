# MQ集成方案

## 一、方案概述

### 1.1 选择MQ
推荐使用 **RocketMQ**（原因：阿里开源，与Spring Boot集成好，支持事务消息，高并发性能优秀）

### 1.2 集成场景
| 场景 | 队列名称 | 用途 |
|------|---------|------|
| 验证码发送 | `verify-code-topic` | 异步发送短信/邮件 |
| 订单通知 | `order-notify-topic` | 订单状态变更通知 |
| 秒杀消息 | `seckill-topic` | 秒杀订单削峰 |

---

## 二、Docker部署RocketMQ

### 2.1 docker-compose.yml
```yaml
version: '3.5'
services:
  rocketmq:
    image: apache/rocketmq:4.9.4
    container_name: rocketmq-namesrv
    ports:
      - 9876:9876
    environment:
      JAVA_OPT_EXT: "-Xms512m -Xmx512m -Xmn256m"
    command: sh mqnamesrv
    networks:
      - rocketmq-net

  rocketmq-console:
    image: apacherocketmq/rocketmq-console:2.0.0
    container_name: rocketmq-console
    ports:
      - 8080:8080
    environment:
      JAVA_OPTS: "-Drocketmq.namesrv.addr=rocketmq:9876 -Dcom.rocketmq.remoting.clientOverrideThreadPoolMaxCapacity=100"
    depends_on:
      - rocketmq
    networks:
      - rocketmq-net

  rocketmq-broker:
    image: apache/rocketmq:4.9.4
    container_name: rocketmq-broker
    ports:
      - 10911:10911
      - 10909:10909
    environment:
      JAVA_OPT_EXT: "-Xms1g -Xmx1g -Xmn512m"
      NAMESRV_ADDR: "rocketmq:9876"
    command: sh mqbroker -c /home/rocketmq/rocketmq-4.9.4/conf/broker.conf
    volumes:
      - ./broker.conf:/home/rocketmq/rocketmq-4.9.4/conf/broker.conf
    depends_on:
      - rocketmq
    networks:
      - rocketmq-net

networks:
  rocketmq-net:
    driver: bridge
```

### 2.2 broker.conf
```properties
brokerClusterName = DefaultCluster
brokerName = broker-a
brokerId = 0
listenPort = 10911
namesrvAddr = rocketmq:9876
storePathRootDir = /home/rocketmq/store
enableAutoCreateTopic = true
autoCreateTopicEnable = true
```

---

## 三、Spring Boot集成

### 3.1 pom.xml 添加依赖
```xml
<!-- service-order/pom.xml -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>4.9.4</version>
</dependency>
```

### 3.2 application.yml 配置
```yaml
rocketmq:
  name-server: localhost:9876
  producer:
    group: cloudtry-producer-group
    send-message-timeout: 30000
    max-message-size: 4194304
    retry-times-when-send-async-failed: 2
  consumer:
    group: cloudtry-consumer-group
    ConsumeThreadMin: 10
    ConsumeThreadMax: 64
```

---

## 四、业务代码实现

### 4.1 验证码发送（异步削峰）

#### Producer（service-user）
```java
// UserAuthServiceImpl.java
@Autowired
private RocketMQTemplate rocketMQTemplate;

public void sendVerificationCode(String account) {
    String code = generateCode();
    redisTemplate.opsForValue().set("verify:code:" + account, code, 5, TimeUnit.MINUTES);
    
    // 发送MQ消息
    rocketMQTemplate.asyncSend("verify-code-topic", 
        VerifyCodeMessage.builder()
            .account(account)
            .code(code)
            .type(isPhone(account) ? "SMS" : "EMAIL")
            .build(),
        new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("验证码消息发送成功: {}", sendResult);
            }
            @Override
            public void onException(Throwable e) {
                log.error("验证码消息发送失败", e);
            }
        });
}
```

#### Consumer（独立通知服务）
```java
@Component
@RocketMQMessageListener(topic = "verify-code-topic", consumerGroup = "verify-code-consumer")
public class VerifyCodeConsumer implements RocketMQListener<VerifyCodeMessage> {
    
    @Override
    public void onMessage(VerifyCodeMessage message) {
        if ("SMS".equals(message.getType())) {
            sendSMS(message.getAccount(), message.getCode());
        } else {
            sendEmail(message.getAccount(), message.getCode());
        }
    }
}
```

---

### 4.2 订单通知（解耦）

#### Producer（service-order）
```java
// OrderServiceImpl.java - 订单创建成功后发送通知
@Autowired
private RocketMQTemplate rocketMQTemplate;

private void sendOrderNotification(Order order) {
    rocketMQTemplate.asyncSend("order-notify-topic",
        OrderNotifyMessage.builder()
            .orderId(order.getId())
            .userId(order.getUserId())
            .status(order.getStatus())
            .build(),
        new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {}
            @Override
            public void onException(Throwable e) {
                log.error("订单通知消息发送失败", e);
            }
        });
}
```

#### Consumer
```java
@Component
@RocketMQMessageListener(topic = "order-notify-topic", consumerGroup = "order-notify-consumer")
public class OrderNotifyConsumer implements RocketMQListener<OrderNotifyMessage> {
    
    @Override
    public void onMessage(OrderNotifyMessage message) {
        // 发送推送/邮件通知
        pushService.sendPush(message.getUserId(), "订单创建成功");
    }
}
```

---

### 4.3 秒杀消息（高并发削峰）

#### Producer（秒杀服务）
```java
// SeckillServiceImpl.java
@Autowired
private RocketMQTemplate rocketMQTemplate;

public void seckill(Long productId, Long userId) {
    // 1. Redis扣减库存（原子操作）
    Long remain = redisTemplate.opsForValue().decrement("seckill:stock:" + productId);
    if (remain == null || remain < 0) {
        redisTemplate.opsForValue().increment("seckill:stock:" + productId);
        throw new SeckillException("商品已抢光");
    }
    
    // 2. 发送秒杀消息到MQ（异步）
    rocketMQTemplate.send("seckill-topic",
        MessageBuilder.withPayload(
            SeckillMessage.builder()
                .productId(productId)
                .userId(userId)
                .timestamp(System.currentTimeMillis())
                .build()
        ).build());
}
```

#### Consumer（秒杀订单处理）
```java
@Component
@RocketMQMessageListener(
    topic = "seckill-topic", 
    consumerGroup = "seckill-consumer",
    ConsumeThreadMin = 50,
    ConsumeThreadMax = 200
)
public class SeckillConsumer implements RocketMQListener<SeckillMessage> {
    
    @Autowired
    private OrderService orderService;
    
    @Override
    public void onMessage(SeckillMessage message) {
        try {
            orderService.createOrder(message.getProductId(), message.getUserId());
        } catch (Exception e) {
            // 库存回滚
            redisTemplate.opsForValue().increment("seckill:stock:" + message.getProductId());
            log.error("秒杀订单创建失败，已回滚库存", e);
        }
    }
}
```

---

## 五、高并发优化

### 5.1 消息堆积处理
```java
// Consumer配置
@RocketMQMessageListener(
    topic = "seckill-topic",
    consumerGroup = "seckill-consumer",
    ConsumeThreadMin = 50,
    ConsumeThreadMax = 200,
    ConsumeMessageBatchMaxSize = 10,  // 批量消费
    maxReconsumeTimes = 3  // 最大重试次数
)
```

### 5.2 消息持久化
```java
// 重要消息需要同步发送
rocketMQTemplate.syncSend("order-notify-topic", message);

// 非重要消息可以异步发送
rocketMQTemplate.asyncSend("verify-code-topic", message, callback);
```

### 5.3 死信队列处理
```java
// 消息处理失败后进入死信队列
@RocketMQMessageListener(
    topic = "DLQ-seckill-topic",
    consumerGroup = "dlq-consumer"
)
public class DeadLetterConsumer implements RocketMQListener<MessageExt> {
    @Override
    public void onMessage(MessageExt message) {
        // 记录日志，人工处理
        log.error("死信消息: {}", new String(message.getBody()));
    }
}
```

---

## 六、监控配置

### 6.1 RocketMQ Console
访问 http://localhost:8080 查看：
- 消息堆积情况
- 消费延迟
- 生产者/消费者状态

---

## 七、总结

| 场景 | 消息类型 | 同步/异步 | 消费者线程 |
|------|---------|----------|-----------|
| 验证码 | 普通消息 | 异步 | 10-20 |
| 订单通知 | 普通消息 | 异步 | 10-20 |
| 秒杀 | 顺序消息 | 异步 | 50-200 |

**优势**：
1. 削峰填谷，保护核心服务
2. 解耦业务，提高系统吞吐量
3. 异步处理，提升用户体验
