package com.atguigu.common.cache;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis缓存同步服务
 * 支持两种机制：
 * 1. Canal监听数据库变更（单实例场景）
 * 2. Redis发布订阅同步多实例缓存（多实例场景）
 */
@Service
public class RedisCacheSyncService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheSyncService.class);

    private static final String CACHE_INVALIDATE_TOPIC = "cache:invalidate";

    private static final Map<String, String> TABLE_NAME_MAPPING = new HashMap<>();
    
    static {
        TABLE_NAME_MAPPING.put("cloudtry_product", "product");
        TABLE_NAME_MAPPING.put("product", "product");
        TABLE_NAME_MAPPING.put("cloudtry_user", "user");
        TABLE_NAME_MAPPING.put("user", "user");
        TABLE_NAME_MAPPING.put("cloudtry_order", "order");
        TABLE_NAME_MAPPING.put("order", "order");
        TABLE_NAME_MAPPING.put("cloudtry_order_item", "order_item");
        TABLE_NAME_MAPPING.put("order_item", "order_item");
        TABLE_NAME_MAPPING.put("cloudtry_cart", "cart");
        TABLE_NAME_MAPPING.put("cart", "cart");
    }

    @Value("${canal.server.host:127.0.0.1}")
    private String canalHost;

    @Value("${canal.server.port:11111}")
    private int canalPort;

    @Value("${canal.destination:example}")
    private String canalDestination;

    @Value("${canal.reconnect.initial-delay-seconds:1}")
    private int reconnectInitialDelaySeconds;

    @Value("${canal.reconnect.max-delay-seconds:60}")
    private int reconnectMaxDelaySeconds;

    private static final double RECONNECT_DELAY_MULTIPLIER = 2.0;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedisMessageListenerContainer redisMessageListenerContainer;

    private final MultiLevelCacheService multiLevelCacheService;

    public RedisCacheSyncService(
            RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer redisMessageListenerContainer,
            @Lazy MultiLevelCacheService multiLevelCacheService) {
        this.redisTemplate = redisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
        this.multiLevelCacheService = multiLevelCacheService;
    }

    private ExecutorService executor;
    private volatile CanalConnector canalConnector;
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * 初始化：启动Canal监听和Redis发布订阅监听
     */
    @PostConstruct
    public void init() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "canal-listener");
            t.setDaemon(true);
            return t;
        });

        // 启动Canal监听（保留现有机制）
        startCanalListener();

        // 增加Redis发布订阅监听（多实例缓存同步）
        redisMessageListenerContainer.addMessageListener(this, new PatternTopic(CACHE_INVALIDATE_TOPIC));
        log.info("Redis缓存同步服务已启动，监听主题: {}", CACHE_INVALIDATE_TOPIC);
    }

    /**
     * 销毁：关闭线程池和Canal连接
     */
    @PreDestroy
    public void destroy() {
        log.info("关闭Redis缓存同步服务...");
        running.set(false);

        // 关闭Canal连接
        if (canalConnector != null) {
            try {
                canalConnector.disconnect();
                log.info("Canal连接已关闭");
            } catch (Exception e) {
                log.error("关闭Canal连接异常", e);
            }
        }

        // 关闭线程池
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("线程池强制关闭");
                }
                log.info("线程池已关闭");
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("线程池关闭被中断");
            }
        }
    }

    /**
     * Canal监听数据库变更（带指数退避重连机制）
     */
    private void startCanalListener() {
        executor.execute(() -> {
            int currentDelay = reconnectInitialDelaySeconds;
            while (running.get()) {
                try {
                    connectAndListen();
                    // 连接成功后重置延迟时间
                    currentDelay = reconnectInitialDelaySeconds;
                } catch (Exception e) {
                    log.error("Canal监听异常，{}秒后重连: {}", currentDelay, e.getMessage());
                    if (running.get()) {
                        try {
                            TimeUnit.SECONDS.sleep(currentDelay);
                            // 指数退避：每次失败后延迟时间翻倍，直到最大值
                            currentDelay = (int) Math.min(
                                currentDelay * RECONNECT_DELAY_MULTIPLIER,
                                reconnectMaxDelaySeconds
                            );
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.info("Canal监听线程被中断，退出重连循环");
                            break;
                        }
                    }
                }
            }
            log.info("Canal监听线程已退出");
        });
    }

    /**
     * 连接Canal并监听数据变更
     */
    private void connectAndListen() {
        canalConnector = CanalConnectors.newSingleConnector(
            new InetSocketAddress(canalHost, canalPort),
            canalDestination,
            "",
            ""
        );
        canalConnector.connect();
        canalConnector.subscribe("cloudtry_user\\..*,cloudtry_product\\..*,cloudtry_order\\..*");
        log.info("Canal客户端已连接到 {}:{}", canalHost, canalPort);

        while (running.get()) {
            // 使用完全限定名避免与org.springframework.data.redis.connection.Message冲突
            com.alibaba.otter.canal.protocol.Message canalMessage = canalConnector.getWithoutAck(100);
            long batchId = canalMessage.getId();
            try {
                if (batchId == -1 || canalMessage.getEntries().isEmpty()) {
                    continue;
                }
                for (CanalEntry.Entry entry : canalMessage.getEntries()) {
                    if (entry.getEntryType() == CanalEntry.EntryType.ROWDATA) {
                        processRowData(entry);
                    }
                }
                canalConnector.ack(batchId);
            } catch (Exception e) {
                canalConnector.rollback(batchId);
                log.error("处理Canal消息异常: {}", e.getMessage());
            }
        }

        // 退出循环时断开连接
        if (canalConnector != null) {
            canalConnector.disconnect();
        }
    }

    /**
     * 处理数据库行数据变更
     */
    private void processRowData(CanalEntry.Entry entry) {
        try {
            CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            String tableName = entry.getHeader().getTableName();

            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                switch (rowChange.getEventType()) {
                    case INSERT:
                    case UPDATE:
                        handleInsertOrUpdate(tableName, rowData);
                        break;
                    case DELETE:
                        handleDelete(tableName, rowData);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            log.error("处理行数据异常: {}", e.getMessage());
        }
    }

    /**
     * 处理插入或更新事件
     */
    private void handleInsertOrUpdate(String tableName, CanalEntry.RowData rowData) {
        String key = extractPrimaryKey(rowData);
        if (key != null) {
            // 统一使用业务对象名作为前缀，格式：{业务对象}:{id}
            String businessName = mapTableNameToBusinessName(tableName);
            String cacheKey = businessName + ":" + key;
            // 删除Redis缓存
            redisTemplate.delete(cacheKey);
            log.info("清除Redis缓存: {} (表名: {})", cacheKey, tableName);

            // 发布缓存失效通知，通知其他实例
            publishInvalidation(cacheKey);
        }
    }

    /**
     * 处理删除事件
     */
    private void handleDelete(String tableName, CanalEntry.RowData rowData) {
        String key = extractPrimaryKey(rowData);
        if (key != null) {
            // 统一使用业务对象名作为前缀，格式：{业务对象}:{id}
            String businessName = mapTableNameToBusinessName(tableName);
            String cacheKey = businessName + ":" + key;
            // 删除Redis缓存
            redisTemplate.delete(cacheKey);
            log.info("清除Redis缓存: {} (表名: {})", cacheKey, tableName);

            // 发布缓存失效通知，通知其他实例
            publishInvalidation(cacheKey);
        }
    }

    /**
     * 将数据库表名映射为业务对象名
     * 例如：cloudtry_product -> product
     *
     * @param tableName 数据库表名
     * @return 业务对象名
     */
    private String mapTableNameToBusinessName(String tableName) {
        String businessName = TABLE_NAME_MAPPING.get(tableName);
        if (businessName != null) {
            return businessName;
        }
        // 如果没有配置映射，尝试去掉前缀
        // 例如：cloudtry_xxx -> xxx
        if (tableName.startsWith("cloudtry_")) {
            return tableName.substring("cloudtry_".length());
        }
        // 默认返回原表名
        return tableName;
    }

    /**
     * 提取主键值
     */
    private String extractPrimaryKey(CanalEntry.RowData rowData) {
        for (CanalEntry.Column column : rowData.getBeforeColumnsList()) {
            if (column.getIsKey()) {
                return column.getValue();
            }
        }
        // 如果before列为空，尝试从after列获取
        for (CanalEntry.Column column : rowData.getAfterColumnsList()) {
            if (column.getIsKey()) {
                return column.getValue();
            }
        }
        return null;
    }

    /**
     * Redis消息监听回调
     * 收到其他实例发布的缓存失效通知时，清除本地缓存
     */
    @Override
    public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("收到缓存失效通知: {}", key);

        // 通知MultiLevelCacheService失效本地缓存
        if (multiLevelCacheService != null) {
            multiLevelCacheService.invalidateLocalCache(key);
        }
    }

    /**
     * 发布缓存失效通知
     * 用于通知其他实例清除本地缓存
     *
     * @param key 缓存键
     */
    public void publishInvalidation(String key) {
        redisTemplate.convertAndSend(CACHE_INVALIDATE_TOPIC, key);
        log.info("发布缓存失效通知: {}", key);
    }

    /**
     * 发布商品缓存失效通知
     *
     * @param productId 商品ID
     */
    public void publishProductInvalidation(Long productId) {
        String key = "product:" + productId;
        publishInvalidation(key);
    }
}
