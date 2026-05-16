package com.atguigu.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {
    
    private static final String CODE_KEY_PREFIX = "verification:code:";
    private static final Duration CODE_EXPIRY = Duration.ofMinutes(5);
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String VERIFY_SCRIPT = """
        local stored = redis.call('GET', KEYS[1])
        if stored and stored == ARGV[1] then
            redis.call('DEL', KEYS[1])
            return 1
        end
        return 0
        """;
    
    public String generateAndStore(String account) {
        if (account == null || account.trim().isEmpty()) {
            throw new IllegalArgumentException("账号不能为空");
        }
        String code = generateCode();
        String key = CODE_KEY_PREFIX + account;
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRY);
        return code;
    }
    
    public boolean verify(String account, String code) {
        if (account == null || account.trim().isEmpty()) {
            return false;
        }
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        String key = CODE_KEY_PREFIX + account;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(VERIFY_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), code);
        return result != null && result == 1L;
    }
    
    public void delete(String account) {
        String key = CODE_KEY_PREFIX + account;
        redisTemplate.delete(key);
    }
    
    public long getExpireTime(String account) {
        String key = CODE_KEY_PREFIX + account;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : 0;
    }
    
    private String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
