package com.atguigu.common.cache;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class HotProductService {

    private static final Logger log = LoggerFactory.getLogger(HotProductService.class);

    private static final String HOT_PREFIX = "hot:product:access:";
    private static final long HOT_THRESHOLD = 10000;
    private static final long STAT_WINDOW = 24 * 3600;

    private final RedisTemplate<String, Object> redisTemplate;

    public void recordAccess(Long productId) {
        String key = HOT_PREFIX + productId;
        redisTemplate.opsForValue().increment(key, 1);
        redisTemplate.expire(key, STAT_WINDOW, TimeUnit.SECONDS);
    }

    /**
     * 获取热点商品ID列表
     * 性能优化：使用SCAN命令替代KEYS命令，避免阻塞Redis
     */
    public Set<Long> getHotProductIds() {
        Set<Long> hotIds = new HashSet<>();

        try {
            // 使用SCAN命令遍历key，避免KEYS命令阻塞Redis
            ScanOptions options = ScanOptions.scanOptions()
                    .match(HOT_PREFIX + "*")
                    .count(100) // 每次扫描的建议数量
                    .build();

            try (Cursor<byte[]> cursor = Objects.requireNonNull(redisTemplate.getConnectionFactory())
                    .getConnection()
                    .keyCommands()
                    .scan(options)) {

                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    processHotKey(key, hotIds);
                }
            }
        } catch (Exception e) {
            log.error("扫描热点商品key失败", e);
        }

        return hotIds;
    }

    /**
     * 处理单个热点key
     */
    private void processHotKey(String key, Set<Long> hotIds) {
        try {
            Object countObj = redisTemplate.opsForValue().get(key);
            if (countObj != null) {
                long count = Long.parseLong(countObj.toString());
                if (count > HOT_THRESHOLD) {
                    String productIdStr = key.replace(HOT_PREFIX, "");
                    try {
                        hotIds.add(Long.parseLong(productIdStr));
                    } catch (NumberFormatException e) {
                        log.warn("无效的热点key: {}", key);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("处理热点key失败: {}", key, e);
        }
    }

    public Long getAccessCount(Long productId) {
        String key = HOT_PREFIX + productId;
        Object countObj = redisTemplate.opsForValue().get(key);
        if (countObj == null) {
            return 0L;
        }
        return Long.parseLong(countObj.toString());
    }
}
