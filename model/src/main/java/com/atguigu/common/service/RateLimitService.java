package com.atguigu.common.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOGIN_FAIL_KEY_PREFIX = "rate:login:fail:";
    private static final int LOGIN_FAIL_MAX = 5;
    private static final String LOGIN_LOCK_KEY_PREFIX = "rate:login:lock:";
    private static final long LOCK_DURATION_SECONDS = 300;

    public boolean isAccountLocked(String account) {
        String lockKey = LOGIN_LOCK_KEY_PREFIX + account;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    public void recordLoginFailure(String account) {
        String failKey = LOGIN_FAIL_KEY_PREFIX + account;
        Long failCount = redisTemplate.opsForValue().increment(failKey);
        
        if (failCount != null && failCount == 1) {
            redisTemplate.expire(failKey, 3600, TimeUnit.SECONDS);
        }
        
        if (failCount != null && failCount >= LOGIN_FAIL_MAX) {
            String lockKey = LOGIN_LOCK_KEY_PREFIX + account;
            redisTemplate.opsForValue().set(lockKey, "1", LOCK_DURATION_SECONDS, TimeUnit.SECONDS);
            redisTemplate.delete(failKey);
            log.warn("账号已被锁定: {}, 锁定时长: {}秒", account, LOCK_DURATION_SECONDS);
        }
    }

    public void clearLoginFailure(String account) {
        String failKey = LOGIN_FAIL_KEY_PREFIX + account;
        redisTemplate.delete(failKey);
    }

    public long getRemainingLockTime(String account) {
        String lockKey = LOGIN_LOCK_KEY_PREFIX + account;
        Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        return ttl != null ? ttl : 0;
    }

    /**
     * 尝试获取限流许可（基于令牌桶算法）
     * 
     * @param key 限流key
     * @param permits 允许的请求数
     * @param windowSeconds 时间窗口（秒）
     * @return true-允许通过，false-被限流
     */
    public boolean tryAcquire(String key, int permits, int windowSeconds) {
        String rateLimitKey = "rate:limit:" + key;
        
        Long currentCount = redisTemplate.opsForValue().increment(rateLimitKey);
        
        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(rateLimitKey, windowSeconds, TimeUnit.SECONDS);
        }
        
        if (currentCount != null && currentCount <= permits) {
            return true;
        }
        
        log.debug("限流触发: key={}, count={}, permits={}", key, currentCount, permits);
        return false;
    }

    /**
     * 重置限流计数
     * 
     * @param key 限流key
     */
    public void resetRateLimit(String key) {
        String rateLimitKey = "rate:limit:" + key;
        redisTemplate.delete(rateLimitKey);
    }
}
