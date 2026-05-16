package com.atguigu.order.consumer;

import com.atguigu.common.mq.SeckillMessage;
import com.atguigu.order.service.OrderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RocketMQMessageListener(
    topic = "seckill-topic",
    consumerGroup = "seckill-consumer-group"
)
@RequiredArgsConstructor
public class SeckillConsumer implements RocketMQListener<SeckillMessage> {

    private static final Logger log = LoggerFactory.getLogger(SeckillConsumer.class);
    private static final String SECKILL_STOCK_KEY_PREFIX = "seckill:stock:";

    private final OrderService orderService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 库存回滚Lua脚本 - 保证原子性，同时清除售罄标记
     * KEYS[1]: stockKey 库存key
     * KEYS[2]: soldOutKey 售罄标记key
     * ARGV[1]: quantity 回滚数量
     */
    private static final String ROLLBACK_SCRIPT = """
        local stockKey = KEYS[1]
        local soldOutKey = KEYS[2]
        local quantity = tonumber(ARGV[1])
        
        redis.call('INCRBY', stockKey, quantity)
        redis.call('DEL', soldOutKey)
        return 1
        """;

    /**
     * 库存回滚Lua脚本实例 - 缓存实例
     */
    private DefaultRedisScript<Long> rollbackScript;

    @PostConstruct
    public void init() {
        rollbackScript = new DefaultRedisScript<>(ROLLBACK_SCRIPT, Long.class);
        log.info("秒杀消费者库存回滚Lua脚本加载完成");
    }

    @Override
    public void onMessage(SeckillMessage message) {
        log.info("收到秒杀消息: productId={}, userId={}", message.getProductId(), message.getUserId());
        try {
            orderService.createOrder(message.getProductId(), message.getUserId());
            log.info("秒杀订单创建成功: productId={}, userId={}", message.getProductId(), message.getUserId());
        } catch (Exception e) {
            log.error("秒杀订单创建失败，回滚库存并清除售罄标记: productId={}, userId={}", 
                    message.getProductId(), message.getUserId(), e);
            // 性能优化：使用Lua脚本原子性回滚库存并清除售罄标记
            rollbackStockWithSoldOutFlag(message.getProductId(), 1);
        }
    }

    /**
     * 使用Lua脚本原子性回滚库存并清除售罄标记
     *
     * @param productId 商品ID
     * @param quantity  回滚数量
     */
    private void rollbackStockWithSoldOutFlag(Long productId, int quantity) {
        try {
            String stockKey = SECKILL_STOCK_KEY_PREFIX + productId;
            String soldOutKey = SECKILL_STOCK_KEY_PREFIX + productId + ":soldout";

            redisTemplate.execute(
                rollbackScript,
                Arrays.asList(stockKey, soldOutKey),
                String.valueOf(quantity)
            );
            log.info("库存回滚并清除售罄标记成功, productId={}, quantity={}", productId, quantity);
        } catch (Exception ex) {
            log.error("库存回滚失败, productId={}, quantity={}", productId, quantity, ex);
            // 回滚失败需要告警处理，可接入监控系统
        }
    }
}
