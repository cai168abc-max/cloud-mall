package com.atguigu.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@SuppressWarnings("EI_EXPOSE_REP2")
public class VerificationCodeService {

    private static final String CODE_KEY_PREFIX = "verification:code:";
    private static final String LIMIT_KEY_PREFIX = "verification:limit:";
    private static final String ATTEMPTS_KEY_PREFIX = "verification:attempts:";
    private static final Duration CODE_EXPIRY = Duration.ofMinutes(5);
    private static final Duration LIMIT_EXPIRY = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VERIFY_SCRIPT = """
        local stored = redis.call('GET', KEYS[1])
        if stored and stored == ARGV[1] then
            redis.call('DEL', KEYS[1])
            return 1
        end
        return 0
        """;

    public String generateAndStore(final String account) {
        if (account == null || account.trim().isEmpty()) {
            throw new IllegalArgumentException("账号不能为空");
        }
        final String limitKey = LIMIT_KEY_PREFIX + account;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            throw new IllegalStateException("操作过于频繁，请稍后再试");
        }
        final String code = generateCode();
        final String key = CODE_KEY_PREFIX + account;
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRY);
        redisTemplate.opsForValue().set(limitKey, "1", LIMIT_EXPIRY);
        return code;
    }

    public boolean verify(final String account, final String code) {
        if (account == null || account.trim().isEmpty()) {
            return false;
        }
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        final String attemptsKey = ATTEMPTS_KEY_PREFIX + account;
        final String codeKey = CODE_KEY_PREFIX + account;
        final Object attemptsObj = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = 0;
        if (attemptsObj != null) {
            attempts = Integer.parseInt(attemptsObj.toString());
        }
        if (attempts >= MAX_ATTEMPTS) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptsKey);
            return false;
        }
        final DefaultRedisScript<Long> script = new DefaultRedisScript<>(VERIFY_SCRIPT, Long.class);
        final Long result = redisTemplate.execute(script, Collections.singletonList(codeKey), code);
        if (result != null && result == 1L) {
            redisTemplate.delete(attemptsKey);
            return true;
        }
        final Long newAttempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (newAttempts != null && newAttempts == 1L) {
            redisTemplate.expire(attemptsKey, CODE_EXPIRY);
        }
        return false;
    }

    public void delete(final String account) {
        final String key = CODE_KEY_PREFIX + account;
        redisTemplate.delete(key);
    }

    public long getExpireTime(final String account) {
        final String key = CODE_KEY_PREFIX + account;
        final Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : 0;
    }

    private String generateCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}
