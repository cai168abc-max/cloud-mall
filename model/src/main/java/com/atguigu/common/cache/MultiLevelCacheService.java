package com.atguigu.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存服务
 * 支持本地缓存(Caffeine) + Redis分布式缓存
 * 通过Redis发布订阅实现多实例缓存一致性
 * 
 * <p>本地缓存配置说明：</p>
 * <ul>
 *   <li>LOCAL_CACHE_EXPIRE_MINUTES: 本地缓存过期时间，默认10分钟，建议5-15分钟</li>
 *   <li>LOCAL_CACHE_MAX_SIZE: 本地缓存最大条目数，默认10000</li>
 *   <li>REDIS_EXPIRE: Redis缓存过期时间，默认1小时</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class MultiLevelCacheService {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheService.class);

    /**
     * Redis缓存键前缀
     */
    private static final String REDIS_KEY_PREFIX = "product:";
    
    /**
     * Redis缓存过期时间（秒），默认1小时
     */
    private static final long REDIS_EXPIRE = 3600;
    
    /**
     * 本地缓存最大条目数
     */
    private static final int LOCAL_CACHE_MAX_SIZE = 10000;

    /**
     * 本地缓存过期时间（分钟），默认10分钟
     * 延长本地缓存时间可减少Redis访问压力，但需配合Canal/Redis发布订阅保证一致性
     */
    @Value("${cache.local.expire-minutes:10}")
    private int localCacheExpireMinutes;

    private final RedisTemplate<String, Object> redisTemplate;

    @Nullable
    private final RedisCacheSyncService redisCacheSyncService;

    private Cache<String, Object> localCache;
    
    /**
     * 初始化本地缓存
     * 使用配置化的过期时间
     */
    @PostConstruct
    public void initLocalCache() {
        this.localCache = Caffeine.newBuilder()
            .maximumSize(LOCAL_CACHE_MAX_SIZE)
            .expireAfterWrite(localCacheExpireMinutes, TimeUnit.MINUTES)
            .recordStats()
            .build();
        log.info("多级缓存服务已初始化，本地缓存最大容量: {}, 过期时间: {}分钟",
            LOCAL_CACHE_MAX_SIZE, localCacheExpireMinutes);
    }

    /**
     * 获取商品缓存
     * 查询顺序：本地缓存 -> Redis缓存 -> 返回null
     *
     * @param productId 商品ID
     * @return 商品对象，如果不存在返回null
     */
    public Object getProduct(final Long productId) {
        String cacheKey = REDIS_KEY_PREFIX + productId;

        // 1. 先查本地缓存
        Object product = localCache.getIfPresent(cacheKey);
        if (product != null) {
            log.debug("命中本地缓存: productId={}", productId);
            return product;
        }

        // 2. 查Redis缓存
        product = redisTemplate.opsForValue().get(cacheKey);

        if (product != null) {
            // 回填本地缓存
            localCache.put(cacheKey, product);
            log.debug("命中Redis缓存: productId={}", productId);
            return product;
        }

        return null;
    }

    /**
     * 设置商品缓存
     * 同时写入本地缓存和Redis缓存
     *
     * @param productId 商品ID
     * @param product   商品对象
     */
    public void setProduct(final Long productId, final Object product) {
        String cacheKey = REDIS_KEY_PREFIX + productId;

        // 写入Redis缓存
        redisTemplate.opsForValue().set(cacheKey, product, REDIS_EXPIRE, TimeUnit.SECONDS);

        // 写入本地缓存
        localCache.put(cacheKey, product);

        log.debug("设置缓存: productId={}", productId);
    }

    /**
     * 失效商品缓存
     * 同时清除本地缓存和Redis缓存，并发布失效通知
     *
     * @param productId 商品ID
     */
    public void invalidate(final Long productId) {
        String cacheKey = REDIS_KEY_PREFIX + productId;

        // 清除Redis缓存
        redisTemplate.delete(cacheKey);

        // 清除本地缓存
        localCache.invalidate(cacheKey);

        // 发布缓存失效通知，通知其他实例
        if (redisCacheSyncService != null) {
            redisCacheSyncService.publishInvalidation(cacheKey);
        }

        log.info("失效缓存: productId={}", productId);
    }

    /**
     * 失效本地缓存
     * 由RedisCacheSyncService收到缓存失效通知后调用
     *
     * @param key 缓存键
     */
    public void invalidateLocalCache(final String key) {
        localCache.invalidate(key);
        log.info("失效本地缓存: key={}", key);
    }

    /**
     * 清空所有本地缓存
     */
    public void clearLocalCache() {
        localCache.invalidateAll();
        log.info("清空所有本地缓存");
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息字符串
     */
    public String getCacheStats() {
        return String.format("本地缓存统计 - 请求数: %d, 命中率: %.2f%%, 驱逐数: %d",
            localCache.stats().requestCount(),
            localCache.stats().hitRate() * 100,
            localCache.stats().evictionCount());
    }

    /**
     * 通用缓存获取方法
     *
     * @param key 缓存键
     * @return 缓存值
     */
    public Object get(final String key) {
        // 先查本地缓存
        Object value = localCache.getIfPresent(key);
        if (value != null) {
            log.debug("命中本地缓存: key={}", key);
            return value;
        }

        // 查Redis缓存
        value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            localCache.put(key, value);
            log.debug("命中Redis缓存: key={}", key);
        }

        return value;
    }

    /**
     * 通用缓存设置方法
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void set(final String key, final Object value) {
        redisTemplate.opsForValue().set(key, value, REDIS_EXPIRE, TimeUnit.SECONDS);
        localCache.put(key, value);
        log.debug("设置缓存: key={}", key);
    }

    /**
     * 通用缓存设置方法（带过期时间）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    public void set(final String key, final Object value, final long timeout, final TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
        localCache.put(key, value);
        log.debug("设置缓存: key={}, timeout={} {}", key, timeout, unit);
    }

    /**
     * 通用缓存删除方法
     *
     * @param key 缓存键
     */
    public void delete(final String key) {
        redisTemplate.delete(key);
        localCache.invalidate(key);

        // 发布缓存失效通知
        if (redisCacheSyncService != null) {
            redisCacheSyncService.publishInvalidation(key);
        }

        log.info("删除缓存: key={}", key);
    }
}
