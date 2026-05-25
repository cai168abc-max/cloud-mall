package com.atguigu.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * HMAC签名工具类
 * 功能说明：
 * 1. 使用HMAC-SHA256算法生成签名
 * 2. 支持时间戳防重放攻击
 * 3. 用于Gateway与微服务之间的请求验证
 * 安全特性：
 * - 使用HMAC-SHA256算法，安全性高
 * - 包含时间戳，防止重放攻击
 * - 签名内容包含请求路径，防止签名被复用
 */
public final class HmacSignatureUtil {

    private HmacSignatureUtil() {
    }

    private static final Logger log = LoggerFactory.getLogger(HmacSignatureUtil.class);

    private static final String HMAC_SHA256 = "HmacSHA256";
    
    // 签名有效期（毫秒），默认5分钟
    private static final long SIGNATURE_VALIDITY_MS = 5 * 60 * 1000;

    /**
     * 生成HMAC签名
     * 
     * @param secret 密钥
     * @param path 请求路径
     * @param timestamp 时间戳
     * @return Base64编码的签名
     */
    public static String generateSignature(final String secret, final String path, final long timestamp) {
        try {
            String data = path + "|" + timestamp;
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("生成HMAC签名失败", e);
            throw new RuntimeException("生成HMAC签名失败", e);
        }
    }

    /**
     * 验证HMAC签名
     * 
     * @param secret 密钥
     * @param path 请求路径
     * @param timestamp 时间戳
     * @param signature 待验证的签名
     * @return 验证结果
     */
    public static boolean verifySignature(final String secret, final String path, 
            final long timestamp, final String signature) {
        // 检查时间戳是否在有效期内
        long currentTime = Instant.now().toEpochMilli();
        if (Math.abs(currentTime - timestamp) > SIGNATURE_VALIDITY_MS) {
            log.warn("签名已过期，timestamp={}, currentTime={}", timestamp, currentTime);
            return false;
        }
        
        // 验证签名
        String expectedSignature = generateSignature(secret, path, timestamp);
        boolean valid = expectedSignature.equals(signature);
        
        if (!valid) {
            log.warn("HMAC签名验证失败，path={}", path);
        }
        
        return valid;
    }

    /**
     * 生成完整的内部请求标识
     * 格式：timestamp:signature
     * 
     * @param secret 密钥
     * @param path 请求路径
     * @return 完整的内部请求标识
     */
    public static String generateInternalRequestToken(final String secret, final String path) {
        long timestamp = Instant.now().toEpochMilli();
        String signature = generateSignature(secret, path, timestamp);
        return timestamp + ":" + signature;
    }

    /**
     * 验证内部请求标识
     * 
     * @param secret 密钥
     * @param path 请求路径
     * @param token 内部请求标识（格式：timestamp:signature）
     * @return 验证结果
     */
    public static boolean verifyInternalRequestToken(final String secret, final String path, final String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        String[] parts = token.split(":");
        if (parts.length != 2) {
            log.warn("内部请求标识格式错误: {}", token);
            return false;
        }
        
        try {
            long timestamp = Long.parseLong(parts[0]);
            String signature = parts[1];
            return verifySignature(secret, path, timestamp, signature);
        } catch (NumberFormatException e) {
            log.warn("内部请求标识时间戳格式错误: {}", token);
            return false;
        }
    }
}
