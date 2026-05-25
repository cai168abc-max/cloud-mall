package com.atguigu.common.utils;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * JWT密钥生成工具类
 * 用于生成符合安全要求的JWT签名密钥
 * 
 * <p>使用示例：</p>
 * <pre>
 * String secret = JwtKeyGenerator.generateSecret(256);
 * System.out.println("Generated JWT Secret: " + secret);
 * </pre>
 * 
 * <p>安全要求：</p>
 * <ul>
 *   <li>密钥长度至少256位（32字节）用于HS256算法</li>
 *   <li>密钥长度至少384位（48字节）用于HS384算法</li>
 *   <li>密钥长度至少512位（64字节）用于HS512算法</li>
 * </ul>
 * 
 * @author Backend Architect
 * @since 1.0.0
 */
public final class JwtKeyGenerator {

    private JwtKeyGenerator() {
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * 默认密钥长度（256位，适用于HS256算法）
     */
    public static final int DEFAULT_KEY_LENGTH = 256;

    /**
     * 最小密钥长度（256位）
     */
    public static final int MIN_KEY_LENGTH = 256;

    /**
     * 生成Base64编码的JWT密钥
     * 
     * @param bitLength 密钥位数（推荐256、384或512）
     * @return Base64编码的密钥字符串
     * @throws IllegalArgumentException 如果密钥长度小于最小要求
     */
    public static String generateSecret(int bitLength) {
        if (bitLength < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException(
                "密钥长度不足，至少需要" + MIN_KEY_LENGTH + "位，当前请求: " + bitLength + "位"
            );
        }
        
        int byteLength = bitLength / 8;
        byte[] randomBytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(randomBytes);
        
        return Base64.getEncoder().encodeToString(randomBytes);
    }

    /**
     * 生成默认长度的JWT密钥（256位，适用于HS256算法）
     * 
     * @return Base64编码的256位密钥字符串
     */
    public static String generateSecret() {
        return generateSecret(DEFAULT_KEY_LENGTH);
    }

    /**
     * 生成适用于HS384算法的JWT密钥（384位）
     * 
     * @return Base64编码的384位密钥字符串
     */
    public static String generateSecretForHS384() {
        return generateSecret(384);
    }

    /**
     * 生成适用于HS512算法的JWT密钥（512位）
     * 
     * @return Base64编码的512位密钥字符串
     */
    public static String generateSecretForHS512() {
        return generateSecret(512);
    }

    /**
     * 验证密钥是否符合安全要求
     * 
     * @param secret 待验证的密钥
     * @return 验证结果，null表示验证通过，否则返回错误信息
     */
    public static String validateSecret(final String secret) {
        if (secret == null || secret.isEmpty()) {
            return "JWT密钥未配置，请设置环境变量JWT_SECRET";
        }
        int bitLength;
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            bitLength = decoded.length * 8;
        } catch (IllegalArgumentException e) {
            bitLength = secret.length() * 8;
        }
        if (bitLength < MIN_KEY_LENGTH) {
            return "JWT密钥长度不足，至少需要" + MIN_KEY_LENGTH + "位，当前实际长度: " + bitLength + "位";
        }
        return null;
    }

    /**
     * 检查密钥是否有效
     * 
     * @param secret 待检查的密钥
     * @return true表示密钥有效，false表示无效
     */
    public static boolean isValidSecret(String secret) {
        return validateSecret(secret) == null;
    }

    /**
     * 打印密钥生成使用说明
     * 
     * @param args 命令行参数
     */
    public static void main(final String[] args) {
        System.out.println("========== JWT密钥生成工具 ==========");
        System.out.println();
        System.out.println("生成HS256算法密钥（256位）:");
        System.out.println("JWT_SECRET=" + generateSecret());
        System.out.println();
        System.out.println("生成HS384算法密钥（384位）:");
        System.out.println("JWT_SECRET=" + generateSecretForHS384());
        System.out.println();
        System.out.println("生成HS512算法密钥（512位）:");
        System.out.println("JWT_SECRET=" + generateSecretForHS512());
        System.out.println();
        System.out.println("========== 使用说明 ==========");
        System.out.println("1. 将生成的密钥设置为环境变量 JWT_SECRET");
        System.out.println("2. 或者在 application.yml 中配置 security.jwt.secret");
        System.out.println("3. 生产环境务必使用环境变量，不要硬编码密钥");
        System.out.println("4. 密钥长度要求：HS256至少256位，HS384至少384位，HS512至少512位");
    }
}
