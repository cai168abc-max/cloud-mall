package com.atguigu.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtKeyGenerator 单元测试类
 * 测试JWT密钥生成和验证功能
 */
@DisplayName("JwtKeyGenerator 单元测试")
class JwtKeyGeneratorTest {

    @Nested
    @DisplayName("generateSecret 方法测试")
    class GenerateSecretTests {

        @Test
        @DisplayName("应该生成默认长度的密钥")
        void should_generateDefaultLengthSecret() {
            // When
            String secret = JwtKeyGenerator.generateSecret();

            // Then
            assertNotNull(secret, "生成的密钥不应为空");
            assertFalse(secret.isEmpty(), "生成的密钥不应为空字符串");
            
            // 验证是有效的Base64编码
            assertDoesNotThrow(() -> Base64.getDecoder().decode(secret), 
                "生成的密钥应该是有效的Base64编码");
        }

        @Test
        @DisplayName("应该生成指定长度的密钥")
        void should_generateSecretWithSpecifiedLength() {
            // Given
            int bitLength = 256;

            // When
            String secret = JwtKeyGenerator.generateSecret(bitLength);

            // Then
            assertNotNull(secret, "生成的密钥不应为空");
            
            byte[] decoded = Base64.getDecoder().decode(secret);
            int actualBitLength = decoded.length * 8;
            assertEquals(bitLength, actualBitLength, "生成的密钥长度应为" + bitLength + "位");
        }

        @Test
        @DisplayName("应该生成384位密钥用于HS384")
        void should_generateSecretForHS384() {
            // When
            String secret = JwtKeyGenerator.generateSecretForHS384();

            // Then
            assertNotNull(secret, "生成的密钥不应为空");
            
            byte[] decoded = Base64.getDecoder().decode(secret);
            assertEquals(48, decoded.length, "HS384密钥应为48字节（384位）");
        }

        @Test
        @DisplayName("应该生成512位密钥用于HS512")
        void should_generateSecretForHS512() {
            // When
            String secret = JwtKeyGenerator.generateSecretForHS512();

            // Then
            assertNotNull(secret, "生成的密钥不应为空");
            
            byte[] decoded = Base64.getDecoder().decode(secret);
            assertEquals(64, decoded.length, "HS512密钥应为64字节（512位）");
        }

        @Test
        @DisplayName("应该拒绝小于最小长度的密钥请求")
        void should_rejectSecretRequestBelowMinimumLength() {
            // Given
            int invalidBitLength = 128;

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JwtKeyGenerator.generateSecret(invalidBitLength)
            );
            assertTrue(exception.getMessage().contains("密钥长度不足"), 
                "异常消息应包含'密钥长度不足'");
        }

        @Test
        @DisplayName("应该拒绝零长度密钥请求")
        void should_rejectZeroLengthSecretRequest() {
            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> JwtKeyGenerator.generateSecret(0));
        }

        @Test
        @DisplayName("应该拒绝负数长度密钥请求")
        void should_rejectNegativeLengthSecretRequest() {
            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> JwtKeyGenerator.generateSecret(-1));
        }

        @Test
        @DisplayName("每次生成的密钥应该不同")
        void should_generateDifferentSecretsEachTime() {
            // When
            String secret1 = JwtKeyGenerator.generateSecret();
            String secret2 = JwtKeyGenerator.generateSecret();

            // Then
            assertNotEquals(secret1, secret2, "每次生成的密钥应该不同");
        }

        @Test
        @DisplayName("应该生成刚好满足最小长度的密钥")
        void should_generateSecretWithMinimumLength() {
            // Given
            int minBitLength = JwtKeyGenerator.MIN_KEY_LENGTH;

            // When
            String secret = JwtKeyGenerator.generateSecret(minBitLength);

            // Then
            assertNotNull(secret, "生成的密钥不应为空");
            
            byte[] decoded = Base64.getDecoder().decode(secret);
            assertEquals(minBitLength / 8, decoded.length, "生成的密钥长度应正确");
        }
    }

    @Nested
    @DisplayName("validateSecret 方法测试")
    class ValidateSecretTests {

        @Test
        @DisplayName("应该验证有效的Base64编码密钥")
        void should_validateValidBase64Secret() {
            // Given
            String validSecret = JwtKeyGenerator.generateSecret();

            // When
            String error = JwtKeyGenerator.validateSecret(validSecret);

            // Then
            assertNull(error, "有效密钥验证应返回null");
        }

        @Test
        @DisplayName("应该拒绝null密钥")
        void should_rejectNullSecret() {
            // When
            String error = JwtKeyGenerator.validateSecret(null);

            // Then
            assertNotNull(error, "null密钥应返回错误信息");
            assertTrue(error.contains("未配置"), "错误信息应包含'未配置'");
        }

        @Test
        @DisplayName("应该拒绝空字符串密钥")
        void should_rejectEmptySecret() {
            // When
            String error = JwtKeyGenerator.validateSecret("");

            // Then
            assertNotNull(error, "空字符串密钥应返回错误信息");
        }

        @Test
        @DisplayName("应该拒绝空白字符串密钥")
        void should_rejectBlankSecret() {
            // When
            String error = JwtKeyGenerator.validateSecret("   ");

            // Then
            assertNotNull(error, "空白字符串密钥应返回错误信息");
        }

        @Test
        @DisplayName("应该拒绝长度不足的Base64密钥")
        void should_rejectShortBase64Secret() {
            // Given
            String shortSecret = Base64.getEncoder().encodeToString("short".getBytes());

            // When
            String error = JwtKeyGenerator.validateSecret(shortSecret);

            // Then
            assertNotNull(error, "长度不足的密钥应返回错误信息");
            assertTrue(error.contains("长度不足"), "错误信息应包含'长度不足'");
        }

        @Test
        @DisplayName("应该拒绝长度不足的非Base64密钥")
        void should_rejectShortNonBase64Secret() {
            String shortNonBase64Secret = "short";
            String error = JwtKeyGenerator.validateSecret(shortNonBase64Secret);
            assertNotNull(error, "长度不足的非Base64密钥应返回错误信息");
        }

        @Test
        @DisplayName("Base64编码密钥应按解码后字节数计算位长度")
        void should_calculateBitLengthFromDecodedBytes_forBase64Secret() {
            byte[] bytes = new byte[24];
            String base64Secret = Base64.getEncoder().encodeToString(bytes);
            String error = JwtKeyGenerator.validateSecret(base64Secret);
            assertNotNull(error);
            assertTrue(error.contains("192"), "24字节Base64密钥应为192位");
        }

        @Test
        @DisplayName("非Base64密钥应按字符数计算位长度")
        void should_calculateBitLengthFromCharCount_forNonBase64Secret() {
            String nonBase64Secret = "!@#$%^&*()!@#$%^&*()!@#$%^&*()ab";
            String error = JwtKeyGenerator.validateSecret(nonBase64Secret);
            assertNull(error, "32字符非Base64密钥应为256位，应通过验证");
        }
    }

    @Nested
    @DisplayName("isValidSecret 方法测试")
    class IsValidSecretTests {

        @Test
        @DisplayName("应该返回true对于有效密钥")
        void should_returnTrueForValidSecret() {
            // Given
            String validSecret = JwtKeyGenerator.generateSecret();

            // When
            boolean isValid = JwtKeyGenerator.isValidSecret(validSecret);

            // Then
            assertTrue(isValid, "有效密钥应返回true");
        }

        @Test
        @DisplayName("应该返回false对于null密钥")
        void should_returnFalseForNullSecret() {
            // When
            boolean isValid = JwtKeyGenerator.isValidSecret(null);

            // Then
            assertFalse(isValid, "null密钥应返回false");
        }

        @Test
        @DisplayName("应该返回false对于空字符串密钥")
        void should_returnFalseForEmptySecret() {
            // When
            boolean isValid = JwtKeyGenerator.isValidSecret("");

            // Then
            assertFalse(isValid, "空字符串密钥应返回false");
        }

        @Test
        @DisplayName("应该返回false对于长度不足的密钥")
        void should_returnFalseForShortSecret() {
            // Given
            String shortSecret = "short";

            // When
            boolean isValid = JwtKeyGenerator.isValidSecret(shortSecret);

            // Then
            assertFalse(isValid, "长度不足的密钥应返回false");
        }
    }

    @Nested
    @DisplayName("常量测试")
    class ConstantTests {

        @Test
        @DisplayName("默认密钥长度应为256位")
        void should_haveCorrectDefaultKeyLength() {
            assertEquals(256, JwtKeyGenerator.DEFAULT_KEY_LENGTH, 
                "默认密钥长度应为256位");
        }

        @Test
        @DisplayName("最小密钥长度应为256位")
        void should_haveCorrectMinimumKeyLength() {
            assertEquals(256, JwtKeyGenerator.MIN_KEY_LENGTH, 
                "最小密钥长度应为256位");
        }
    }
}
