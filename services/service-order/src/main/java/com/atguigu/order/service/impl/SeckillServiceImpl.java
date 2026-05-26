package com.atguigu.order.service.impl;

import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.mq.SeckillMessage;
import com.atguigu.order.bean.Order;
import com.atguigu.common.context.UserContext;
import com.atguigu.order.feign.ProductFeign;
import com.atguigu.order.service.OrderService;
import com.atguigu.order.service.SeckillService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class SeckillServiceImpl implements SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillServiceImpl.class);

    private static final String SECKILL_STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String SECKILL_USER_KEY_PREFIX = "seckill:user:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final OrderService orderService;
    private final ProductFeign productFeign;
    private final RocketMQTemplate rocketMQTemplate;
    private final RedissonClient redissonClient;

    private DefaultRedisScript<Long> seckillDeductScript;

    private static final String ROLLBACK_SCRIPT = """
        local stockKey = KEYS[1]
        local soldOutKey = KEYS[2]
        local quantity = tonumber(ARGV[1])
        
        redis.call('INCRBY', stockKey, quantity)
        redis.call('DEL', soldOutKey)
        return 1
        """;

    private DefaultRedisScript<Long> rollbackScript;

    @PostConstruct
    public void init() throws IOException {
        ClassPathResource resource = new ClassPathResource("lua/seckill_deduct.lua");
        String scriptContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        seckillDeductScript = new DefaultRedisScript<>(scriptContent, Long.class);
        log.info("秒杀库存扣减Lua脚本加载完成");

        rollbackScript = new DefaultRedisScript<>(ROLLBACK_SCRIPT, Long.class);
        log.info("秒杀库存回滚Lua脚本加载完成");
    }

    @Override
    public Order seckill(Long productId, Long userId) {
        UserInfo user = UserContext.get();
        Long realUserId = userId != null ? userId : (user != null ? user.getId() : null);
        if (realUserId == null) {
            throw new RuntimeException("用户未登录");
        }

        String userKey = SECKILL_USER_KEY_PREFIX + realUserId + ":" + productId;
        Boolean isFirst = redisTemplate.opsForValue().setIfAbsent(userKey, "1", 24, java.util.concurrent.TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isFirst)) {
            log.info("用户重复秒杀, userId={}, productId={}", realUserId, productId);
            throw new RuntimeException("您已参与过此商品秒杀");
        }

        String stockKey = SECKILL_STOCK_KEY_PREFIX + productId;
        String soldOutKey = SECKILL_STOCK_KEY_PREFIX + productId + ":soldout";

        Long remain = redisTemplate.execute(
            seckillDeductScript,
            Arrays.asList(stockKey, soldOutKey),
            "1", "3600"
        );

        if (remain == null || remain < 0) {
            redisTemplate.delete(userKey);
            log.info("秒杀库存不足, productId={}, remain={}", productId, remain);
            return null;
        }

        try {
            rocketMQTemplate.asyncSend("seckill-topic",
                SeckillMessage.builder()
                    .productId(productId)
                    .userId(realUserId)
                    .timestamp(System.currentTimeMillis())
                    .build(),
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("秒杀消息发送成功: {}", sendResult);
                    }
                    @Override
                    public void onException(Throwable e) {
                        log.error("秒杀消息发送失败，回滚库存", e);
                        rollbackStock(stockKey, soldOutKey, 1);
                        redisTemplate.delete(userKey);
                    }
                });

            return Order.builder().id(0L).status(com.atguigu.common.enums.OrderStatus.CREATED).build();
        } catch (Exception e) {
            redisTemplate.delete(userKey);
            rollbackStock(stockKey, soldOutKey, 1);
            log.error("秒杀异常", e);
            throw new RuntimeException("秒杀失败");
        }
    }

    private void rollbackStock(String stockKey, String soldOutKey, int quantity) {
        try {
            redisTemplate.execute(
                rollbackScript,
                Arrays.asList(stockKey, soldOutKey),
                String.valueOf(quantity)
            );
            log.info("库存回滚成功, stockKey={}, quantity={}", stockKey, quantity);
        } catch (Exception e) {
            log.error("库存回滚失败, stockKey={}, quantity={}", stockKey, quantity, e);
        }
    }
}
