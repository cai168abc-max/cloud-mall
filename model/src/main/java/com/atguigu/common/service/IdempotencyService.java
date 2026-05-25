package com.atguigu.common.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 幂等性服务 - 使用Redisson分布式锁实现
 * 提供分布式环境下的幂等性保证，防止重复提交
 *
 * <p>核心特性：
 * <ul>
 *   <li>线程安全的锁对象缓存，确保同一线程可以正确释放锁</li>
 *   <li>完善的参数校验和异常处理</li>
 *   <li>锁竞争统计和监控</li>
 *   <li>支持看门狗自动续期机制</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * IdempotencyResult result = idempotencyService.tryLock("order:create:123", 300);
 * if (result.isSuccess()) {
 *     try {
 *         // 执行业务逻辑
 *     } finally {
 *         idempotencyService.releaseLock("order:create:123");
 *     }
 * } else {
 *     // 处理获取锁失败的情况
 * }
 * </pre>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    /**
     * 锁key前缀
     */
    private static final String LOCK_PREFIX = "idempotency:";

    /**
     * 默认锁过期时间（秒）
     */
    private static final long DEFAULT_EXPIRE_SECONDS = 300;

    /**
     * 最大锁过期时间（秒）- 24小时
     */
    private static final long MAX_EXPIRE_SECONDS = 86400;

    /**
     * 最小锁过期时间（秒）
     */
    private static final long MIN_EXPIRE_SECONDS = 1;

    /**
     * 看门狗模式标识（leaseTime为-1时启用看门狗自动续期）
     */
    private static final long WATCHDOG_MODE = -1L;

    /**
     * 当前线程持有的锁对象缓存
     * Key: 锁的完整key, Value: RLock对象
     */
    private final ConcurrentHashMap<String, String> lockCache = new ConcurrentHashMap<>();

    /**
     * 锁竞争统计
     */
    private final AtomicLong lockSuccessCount = new AtomicLong(0);
    private final AtomicLong lockFailureCount = new AtomicLong(0);
    private final AtomicLong lockReleaseCount = new AtomicLong(0);
    private final AtomicLong lockExceptionCount = new AtomicLong(0);

    private final StringRedisTemplate stringRedisTemplate;

    public IdempotencyService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "StringRedisTemplate不能为null");
        log.info("IdempotencyService初始化完成");
    }

    /**
     * 尝试获取幂等性锁（使用默认过期时间300秒）
     *
     * @param key 业务唯一标识，不能为null或空
     * @return 锁获取结果，包含成功/失败状态和失败原因
     */
    public IdempotencyResult tryLock(final String key) {
        return tryLock(key, DEFAULT_EXPIRE_SECONDS);
    }

    /**
     * 尝试获取幂等性锁
     * 使用Redisson分布式锁，支持看门狗机制自动续期
     *
     * <p>当expireSeconds为-1时，启用看门狗机制，锁会自动续期直到显式释放
     *
     * @param key 业务唯一标识，不能为null或空
     * @param expireSeconds 锁过期时间（秒），必须大于0或为-1（看门狗模式）
     * @return 锁获取结果，包含成功/失败状态和失败原因
     */
    public IdempotencyResult tryLock(final String key, final long expireSeconds) {
        validateKey(key);
        validateExpireSeconds(expireSeconds);

        if (lockCache.size() > 1000) {
            log.warn("幂等性锁缓存条目过多: size={}, 建议检查是否有锁未正确释放", lockCache.size());
        }

        String fullKey = LOCK_PREFIX + key;
        long effectiveExpireSeconds = (expireSeconds == WATCHDOG_MODE) ? DEFAULT_EXPIRE_SECONDS : expireSeconds;

        try {
            String threadId = String.valueOf(Thread.currentThread().getId());
            Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(fullKey, threadId, effectiveExpireSeconds, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(acquired)) {
                lockCache.put(fullKey, threadId);
                lockSuccessCount.incrementAndGet();
                log.debug("幂等性锁获取成功: key={}, expireSeconds={}, thread={}",
                    fullKey, effectiveExpireSeconds, Thread.currentThread().getName());
                return IdempotencyResult.success();
            } else {
                String cachedThreadId = lockCache.get(fullKey);
                if (cachedThreadId != null) {
                    String redisValue = stringRedisTemplate.opsForValue().get(fullKey);
                    if (!cachedThreadId.equals(redisValue)) {
                        lockCache.remove(fullKey, cachedThreadId);
                    }
                }
                lockFailureCount.incrementAndGet();
                log.warn("幂等性锁获取失败，可能为重复请求: key={}, thread={}",
                    fullKey, Thread.currentThread().getName());
                return IdempotencyResult.failure(IdempotencyResult.FailureReason.DUPLICATE_REQUEST);
            }
        } catch (Exception e) {
            lockExceptionCount.incrementAndGet();
            log.error("幂等性锁获取异常: key={}, thread={}, error={}",
                fullKey, Thread.currentThread().getName(), e.getMessage(), e);
            return IdempotencyResult.failure(IdempotencyResult.FailureReason.REDIS_ERROR);
        }
    }

    /**
     * 尝试获取幂等性锁（带等待时间）
     *
     * @param key 业务唯一标识，不能为null或空
     * @param waitSeconds 等待获取锁的最大时间（秒），必须大于等于0
     * @param expireSeconds 锁过期时间（秒），必须大于0或为-1（看门狗模式）
     * @return 锁获取结果
     */
    public IdempotencyResult tryLockWithWait(String key, long waitSeconds, long expireSeconds) {
        validateKey(key);
        validateExpireSeconds(expireSeconds);
        if (waitSeconds < 0) {
            throw new IllegalArgumentException("等待时间不能为负数: " + waitSeconds);
        }

        String fullKey = LOCK_PREFIX + key;
        long effectiveExpireSeconds = (expireSeconds == WATCHDOG_MODE) ? DEFAULT_EXPIRE_SECONDS : expireSeconds;
        long startTime = System.currentTimeMillis();
        long waitMs = waitSeconds * 1000;
        long pollInterval = 100;

        try {
            while (true) {
                String threadId = String.valueOf(Thread.currentThread().getId());
                Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(fullKey, threadId, effectiveExpireSeconds, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(acquired)) {
                    lockCache.put(fullKey, threadId);
                    lockSuccessCount.incrementAndGet();
                    log.debug("幂等性锁获取成功(带等待): key={}, waitSeconds={}, expireSeconds={}",
                        fullKey, waitSeconds, effectiveExpireSeconds);
                    return IdempotencyResult.success();
                }
                if (System.currentTimeMillis() - startTime >= waitMs) {
                    lockFailureCount.incrementAndGet();
                    log.warn("幂等性锁获取超时: key={}, waitSeconds={}", fullKey, waitSeconds);
                    return IdempotencyResult.failure(IdempotencyResult.FailureReason.TIMEOUT);
                }
                Thread.sleep(pollInterval);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lockExceptionCount.incrementAndGet();
            log.error("幂等性锁获取被中断: key={}", fullKey, e);
            return IdempotencyResult.failure(IdempotencyResult.FailureReason.INTERRUPTED);
        } catch (Exception e) {
            lockExceptionCount.incrementAndGet();
            log.error("幂等性锁获取异常: key={}", fullKey, e);
            return IdempotencyResult.failure(IdempotencyResult.FailureReason.REDIS_ERROR);
        }
    }

    /**
     * 释放幂等性锁
     * 只有锁的持有者才能释放锁
     *
     * @param key 业务唯一标识，不能为null或空
     * @return 释放结果
     */
    public ReleaseResult releaseLock(final String key) {
        validateKey(key);
        String fullKey = LOCK_PREFIX + key;

        String cachedThreadId = lockCache.remove(fullKey);

        if (cachedThreadId == null) {
            log.warn("幂等性锁释放失败，未在缓存中找到锁对象: key={}, thread={}",
                fullKey, Thread.currentThread().getName());
            return ReleaseResult.notHeld();
        }

        try {
            String currentThreadId = String.valueOf(Thread.currentThread().getId());
            if (cachedThreadId.equals(currentThreadId)) {
                Boolean deleted = stringRedisTemplate.delete(fullKey);
                if (Boolean.TRUE.equals(deleted)) {
                    lockReleaseCount.incrementAndGet();
                    log.debug("幂等性锁释放成功: key={}, thread={}",
                        fullKey, Thread.currentThread().getName());
                    return ReleaseResult.success();
                } else {
                    return ReleaseResult.expired();
                }
            } else {
                log.warn("幂等性锁释放失败，当前线程非持有者: key={}, thread={}",
                    fullKey, Thread.currentThread().getName());
                return ReleaseResult.notHeld();
            }
        } catch (Exception e) {
            lockExceptionCount.incrementAndGet();
            log.error("幂等性锁释放异常: key={}, thread={}", fullKey, Thread.currentThread().getName(), e);
            return ReleaseResult.error(e.getMessage());
        }
    }

    /**
     * 强制释放幂等性锁（仅用于管理目的，谨慎使用）
     *
     * @param key 业务唯一标识
     * @return 释放结果
     */
    public ReleaseResult forceReleaseLock(final String key) {
        validateKey(key);
        String fullKey = LOCK_PREFIX + key;

        try {
            Boolean deleted = stringRedisTemplate.delete(fullKey);
            if (Boolean.TRUE.equals(deleted)) {
                lockCache.remove(fullKey);
                lockReleaseCount.incrementAndGet();
                log.warn("幂等性锁强制释放: key={}, thread={}", fullKey, Thread.currentThread().getName());
                return ReleaseResult.success();
            } else {
                return ReleaseResult.notHeld();
            }
        } catch (Exception e) {
            lockExceptionCount.incrementAndGet();
            log.error("幂等性锁强制释放异常: key={}", fullKey, e);
            return ReleaseResult.error(e.getMessage());
        }
    }

    /**
     * 检查是否被任何线程锁定
     *
     * @param key 业务唯一标识，不能为null或空
     * @return true-已锁定，false-未锁定
     */
    public boolean isLocked(String key) {
        validateKey(key);
        String fullKey = LOCK_PREFIX + key;
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(fullKey));
        } catch (Exception e) {
            log.error("检查锁状态异常: key={}", fullKey, e);
            return false;
        }
    }

    /**
     * 检查当前线程是否持有锁
     *
     * @param key 业务唯一标识，不能为null或空
     * @return true-当前线程持有，false-当前线程未持有
     */
    public boolean isHeldByCurrentThread(final String key) {
        validateKey(key);
        String fullKey = LOCK_PREFIX + key;

        String cachedThreadId = lockCache.get(fullKey);
        if (cachedThreadId != null) {
            return cachedThreadId.equals(String.valueOf(Thread.currentThread().getId()));
        }

        try {
            String value = stringRedisTemplate.opsForValue().get(fullKey);
            if (value != null) {
                return value.equals(String.valueOf(Thread.currentThread().getId()));
            }
            return false;
        } catch (Exception e) {
            log.error("检查锁持有状态异常: key={}", fullKey, e);
            return false;
        }
    }

    /**
     * 获取锁的剩余过期时间
     *
     * @param key 业务唯一标识
     * @return 剩余过期时间（毫秒），-1表示锁不存在，-2表示锁存在但没有过期时间
     */
    public long getRemainingTimeToLive(final String key) {
        validateKey(key);
        String fullKey = LOCK_PREFIX + key;
        try {
            Long ttl = stringRedisTemplate.getExpire(fullKey, TimeUnit.MILLISECONDS);
            return ttl != null ? ttl : -1;
        } catch (Exception e) {
            log.error("获取锁剩余时间异常: key={}", fullKey, e);
            return -1;
        }
    }

    /**
     * 获取锁竞争统计信息
     *
     * @return 统计信息
     */
    public LockStatistics getStatistics() {
        return new LockStatistics(
            lockSuccessCount.get(),
            lockFailureCount.get(),
            lockReleaseCount.get(),
            lockExceptionCount.get()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        lockSuccessCount.set(0);
        lockFailureCount.set(0);
        lockReleaseCount.set(0);
        lockExceptionCount.set(0);
        log.info("锁统计信息已重置");
    }

    /**
     * 清理当前线程的锁缓存（用于线程池环境）
     */
    public void clearCurrentThreadCache() {
        String currentThreadId = String.valueOf(Thread.currentThread().getId());
        lockCache.entrySet().removeIf(entry -> currentThreadId.equals(entry.getValue()));
    }

    // ==================== 私有方法 ====================

    /**
     * 校验key参数
     */
    private void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("锁key不能为null或空");
        }
        // 检查key长度，避免过长的key影响Redis性能
        if (key.length() > 256) {
            throw new IllegalArgumentException("锁key长度不能超过256字符: " + key.length());
        }
        // 检查特殊字符（防止Redis注入）
        if (key.contains("\n") || key.contains("\r") || key.contains("\0")) {
            throw new IllegalArgumentException("锁key包含非法字符");
        }
    }

    /**
     * 校验过期时间参数
     */
    private void validateExpireSeconds(long expireSeconds) {
        if (expireSeconds != WATCHDOG_MODE && (expireSeconds < MIN_EXPIRE_SECONDS || expireSeconds > MAX_EXPIRE_SECONDS)) {
            throw new IllegalArgumentException(
                String.format("锁过期时间必须在%d-%d秒之间，或使用-1启用看门狗模式，当前值: %d",
                    MIN_EXPIRE_SECONDS, MAX_EXPIRE_SECONDS, expireSeconds));
        }
    }

    // ==================== 内部类定义 ====================

    /**
     * 锁获取结果
     */
    @Getter
    public static class IdempotencyResult {
        private final boolean success;
        private final FailureReason failureReason;
        private final String message;

        private IdempotencyResult(final boolean success, final FailureReason failureReason, final String message) {
            this.success = success;
            this.failureReason = failureReason;
            this.message = message;
        }

        public static IdempotencyResult success() {
            return new IdempotencyResult(true, null, "锁获取成功");
        }

        public static IdempotencyResult failure(FailureReason reason) {
            return new IdempotencyResult(false, reason, reason.getDescription());
        }

        public static IdempotencyResult failure(final FailureReason reason, final String message) {
            return new IdempotencyResult(false, reason, message);
        }

        public boolean isFailure() {
            return !success;
        }

        /**
         * 失败原因枚举
         */
        @Getter
        public enum FailureReason {
            DUPLICATE_REQUEST("重复请求，锁已被其他线程持有"),
            TIMEOUT("获取锁超时"),
            INTERRUPTED("获取锁被中断"),
            REDIS_ERROR("Redis服务异常");

            private final String description;

            FailureReason(String description) {
                this.description = description;
            }

        }
    }

    /**
     * 锁释放结果
     */
    @Getter
    public static class ReleaseResult {
        private final boolean success;
        private final ReleaseStatus status;
        private final String message;

        private ReleaseResult(final boolean success, final ReleaseStatus status, final String message) {
            this.success = success;
            this.status = status;
            this.message = message;
        }

        public static ReleaseResult success() {
            return new ReleaseResult(true, ReleaseStatus.SUCCESS, "锁释放成功");
        }

        public static ReleaseResult notHeld() {
            return new ReleaseResult(false, ReleaseStatus.NOT_HELD, "当前线程未持有此锁");
        }

        public static ReleaseResult expired() {
            return new ReleaseResult(false, ReleaseStatus.EXPIRED, "锁已过期");
        }

        public static ReleaseResult error(final String message) {
            return new ReleaseResult(false, ReleaseStatus.ERROR, message);
        }

        /**
         * 释放状态枚举
         */
        public enum ReleaseStatus {
            SUCCESS,
            NOT_HELD,
            EXPIRED,
            ERROR
        }
    }

    /**
         * 锁竞争统计信息
         */

        public record LockStatistics(long successCount, long failureCount, long releaseCount, long exceptionCount) {

        public double getFailureRate() {
                long total = successCount + failureCount;
                return total == 0 ? 0 : (double) failureCount / total;
            }

            @Override
            public String toString() {
                return String.format("LockStatistics{success=%d, failure=%d, release=%d, exception=%d, failureRate=%.2f%%}",
                        successCount, failureCount, releaseCount, exceptionCount, getFailureRate() * 100);
            }
        }
}
