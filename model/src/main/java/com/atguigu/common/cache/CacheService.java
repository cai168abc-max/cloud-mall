package com.atguigu.common.cache;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 缓存服务
 * 提供缓存操作、双删策略、互斥锁防击穿等功能
 * 
 * <p>配置项说明：</p>
 * <ul>
 *   <li>cache.double-delete.delay-ms: 双删策略延迟时间（毫秒），默认1000ms</li>
 *   <li>cache.lock.wait-timeout-ms: 获取锁等待超时时间（毫秒），默认3000ms</li>
 *   <li>cache.lock.expire-seconds: 锁过期时间（秒），默认30秒</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    /**
     * 空值占位符，用于防止缓存穿透
     */
    private static final String NULL_VALUE = "\0__NULL_CACHE__\0";
    
    /**
     * 空值缓存TTL（秒）
     */
    private static final long NULL_CACHE_TTL = 60;
    
    /**
     * 基础过期时间（秒）
     */
    private static final int BASE_EXPIRE_TIME = 3600;
    
    /**
     * 随机过期时间范围（秒），用于防止缓存雪崩
     */
    private static final int RANDOM_EXPIRE_TIME = 1800;

    /**
     * 互斥锁相关常量
     */
    private static final String LOCK_PREFIX = "cache:lock:";
    
    /**
     * 获取锁等待超时时间（毫秒）
     */
    @Value("${cache.lock.wait-timeout-ms:3000}")
    private long lockWaitTimeout;
    
    /**
     * 锁过期时间（秒）
     */
    @Value("${cache.lock.expire-seconds:30}")
    private long lockExpireTime;
    
    /**
     * 锁重试间隔（毫秒）
     */
    private static final long LOCK_RETRY_INTERVAL = 50;

    /**
     * 双删策略延迟时间（毫秒）
     * 默认1秒，可根据数据库主从同步延迟调整
     */
    @Value("${cache.double-delete.delay-ms:1000}")
    private long doubleDeleteDelay;

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5, new NamedThreadFactory("cache-delete"));

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedissonClient redissonClient;

    @PreDestroy
    public void destroy() {
        log.info("关闭CacheService线程池...");
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
                log.warn("CacheService线程池强制关闭");
            } else {
                log.info("CacheService线程池已优雅关闭");
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void setCache(String key, Object value) {
        long expireTime = getRandomExpireTime();
        redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.SECONDS);
    }

    public Object getCache(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void setNullCache(String key) {
        redisTemplate.opsForValue().set(key, NULL_VALUE, NULL_CACHE_TTL, TimeUnit.SECONDS);
    }

    public boolean isNullCache(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return NULL_VALUE.equals(value);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public long getRandomExpireTime() {
        return BASE_EXPIRE_TIME + ThreadLocalRandom.current().nextInt(RANDOM_EXPIRE_TIME);
    }

    /**
     * 缓存双删策略
     * 先删除缓存，然后延迟指定时间后再删除一次
     * 用于解决并发情况下的缓存一致性问题
     *
     * @param key 缓存键
     * @param delayMillis 延迟时间（毫秒）
     */
    public void deleteWithDoubleRemoval(String key, long delayMillis) {
        // 第一次删除
        delete(key);
        log.info("缓存双删策略：第一次删除缓存，key={}", key);

        // 延迟后第二次删除
        scheduledExecutor.schedule(() -> {
            delete(key);
            log.info("缓存双删策略：延迟{}ms后第二次删除缓存，key={}", delayMillis, key);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 缓存双删策略（使用配置的默认延迟时间）
     *
     * @param key 缓存键
     */
    public void deleteWithDoubleRemoval(String key) {
        deleteWithDoubleRemoval(key, doubleDeleteDelay);
    }

    /**
     * 带互斥锁的缓存获取 - 防止缓存击穿
     * 当热点Key过期时，只有一个线程去加载数据，其他线程等待
     *
     * @param key 缓存键
     * @param loader 数据加载器（当缓存不存在时调用）
     * @param <T> 返回类型
     * @return 缓存值或从loader加载的值
     */
    @SuppressWarnings("unchecked")
    public <T> T getCacheWithMutex(String key, Supplier<T> loader) {
        // 1. 先尝试从缓存获取
        Object value = getCache(key);
        if (value != null) {
            if (NULL_VALUE.equals(value)) {
                return null; // 防止缓存穿透的空值
            }
            return (T) value;
        }

        // 2. 缓存不存在，尝试获取互斥锁
        String lockKey = LOCK_PREFIX + key;
        return acquireLockAndLoad(key, lockKey, loader);
    }

    /**
     * 获取锁并加载数据
     */
    private <T> T acquireLockAndLoad(String key, String lockKey, Supplier<T> loader) {
        RLock lock = redissonClient.getLock(lockKey);
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < lockWaitTimeout) {
            try {
                if (lock.tryLock(0, lockExpireTime, TimeUnit.SECONDS)) {
                    try {
                        log.info("获取缓存互斥锁成功，开始加载数据, key={}", key);
                        T data = loader.get();
                        if (data != null) {
                            setCache(key, data);
                        } else {
                            setNullCache(key);
                        }
                        return data;
                    } finally {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                        log.info("释放缓存互斥锁, key={}", key);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待缓存锁被中断, key={}", key);
                break;
            }

            try {
                Thread.sleep(LOCK_RETRY_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待缓存锁被中断, key={}", key);
                break;
            }

            Object value = getCache(key);
            if (value != null) {
                if (NULL_VALUE.equals(value)) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                T cachedData = (T) value;
                return cachedData;
            }
        }

        log.warn("获取缓存互斥锁超时，降级直接查询数据源, key={}", key);
        return loader.get();
    }

    /**
     * 自定义线程工厂，用于命名线程池中的线程
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix + "-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
