package com.atguigu.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordUtil 单元测试类
 * 测试密码加密和验证功能
 */
@DisplayName("PasswordUtil 单元测试")
class PasswordUtilTest {

    @Nested
    @DisplayName("hashPassword 方法测试")
    class HashPasswordTests {

        @Test
        @DisplayName("应该成功加密密码")
        void should_hashPassword_successfully() {
            // Given
            String rawPassword = "testPassword123";

            // When
            String hashedPassword = PasswordUtil.hashPassword(rawPassword);

            // Then
            assertNotNull(hashedPassword, "加密后的密码不应为空");
            assertTrue(hashedPassword.length() > 0, "加密后的密码长度应大于0");
            assertNotEquals(rawPassword, hashedPassword, "加密后的密码应与原始密码不同");
        }

        @Test
        @DisplayName("相同密码每次加密结果应不同（盐值随机）")
        void should_returnDifferentHash_forSamePassword() {
            // Given
            String rawPassword = "testPassword123";

            // When
            String hash1 = PasswordUtil.hashPassword(rawPassword);
            String hash2 = PasswordUtil.hashPassword(rawPassword);

            // Then
            assertNotEquals(hash1, hash2, "相同密码每次加密结果应不同");
        }

        @Test
        @DisplayName("应该正确加密空密码")
        void should_hashEmptyPassword_successfully() {
            // Given
            String emptyPassword = "";

            // When
            String hashedPassword = PasswordUtil.hashPassword(emptyPassword);

            // Then
            assertNotNull(hashedPassword, "空密码加密后不应为null");
        }

        @Test
        @DisplayName("应该正确加密包含特殊字符的密码")
        void should_hashPasswordWithSpecialChars_successfully() {
            // Given
            String passwordWithSpecialChars = "test@#$%^&*()Password!";

            // When
            String hashedPassword = PasswordUtil.hashPassword(passwordWithSpecialChars);

            // Then
            assertNotNull(hashedPassword, "包含特殊字符的密码应能正确加密");
        }

        @Test
        @DisplayName("应该正确加密长密码")
        void should_hashLongPassword_successfully() {
            // Given
            String longPassword = "a".repeat(1000);

            // When
            String hashedPassword = PasswordUtil.hashPassword(longPassword);

            // Then
            assertNotNull(hashedPassword, "长密码应能正确加密");
        }

        @Test
        @DisplayName("应该拒绝null密码")
        void should_throwException_whenNullPasswordProvided() {
            assertThrows(IllegalArgumentException.class, () -> {
                PasswordUtil.hashPassword(null);
            }, "null密码应该抛出异常");
        }
    }

    @Nested
    @DisplayName("matches 方法测试")
    class MatchesTests {

        @Test
        @DisplayName("应该正确验证匹配的密码")
        void should_returnTrue_whenPasswordMatches() {
            // Given
            String rawPassword = "correctPassword";
            String hashedPassword = PasswordUtil.hashPassword(rawPassword);

            // When
            boolean matches = PasswordUtil.matches(rawPassword, hashedPassword);

            // Then
            assertTrue(matches, "正确的密码应该匹配");
        }

        @Test
        @DisplayName("应该拒绝不匹配的密码")
        void should_returnFalse_whenPasswordDoesNotMatch() {
            // Given
            String rawPassword = "correctPassword";
            String wrongPassword = "wrongPassword";
            String hashedPassword = PasswordUtil.hashPassword(rawPassword);

            // When
            boolean matches = PasswordUtil.matches(wrongPassword, hashedPassword);

            // Then
            assertFalse(matches, "错误的密码不应该匹配");
        }

        @Test
        @DisplayName("应该区分大小写")
        void should_beCaseSensitive() {
            // Given
            String rawPassword = "Password123";
            String upperCasePassword = "PASSWORD123";
            String hashedPassword = PasswordUtil.hashPassword(rawPassword);

            // When
            boolean matches = PasswordUtil.matches(upperCasePassword, hashedPassword);

            // Then
            assertFalse(matches, "密码验证应区分大小写");
        }

        @Test
        @DisplayName("应该拒绝空密码")
        void should_returnFalse_whenEmptyPasswordProvided() {
            // Given
            String rawPassword = "somePassword";
            String hashedPassword = PasswordUtil.hashPassword(rawPassword);

            // When
            boolean matches = PasswordUtil.matches("", hashedPassword);

            // Then
            assertFalse(matches, "空密码不应该匹配");
        }

        @Test
        @DisplayName("应该拒绝null密码")
        void should_returnFalse_whenNullPasswordProvided() {
            // Given
            String rawPassword = "somePassword";
            String hashedPassword = PasswordUtil.hashPassword(rawPassword);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                PasswordUtil.matches(null, hashedPassword);
            }, "null密码应该抛出异常");
        }

        @Test
        @DisplayName("应该拒绝null哈希值")
        void should_returnFalse_whenNullHashProvided() {
            // Given
            String rawPassword = "somePassword";

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                PasswordUtil.matches(rawPassword, null);
            }, "null哈希值应该抛出异常");
        }

        @Test
        @DisplayName("应该拒绝无效格式的哈希值")
        void should_returnFalse_whenInvalidHashProvided() {
            // Given
            String rawPassword = "somePassword";
            String invalidHash = "invalid_hash_format";

            // When
            boolean matches = PasswordUtil.matches(rawPassword, invalidHash);

            // Then
            assertFalse(matches, "无效格式的哈希值不应该匹配");
        }
    }

    @Nested
    @DisplayName("集成测试：加密后验证")
    class IntegrationTests {

        @Test
        @DisplayName("加密后验证完整流程")
        void should_verifyHashedPassword_successfully() {
            // Given
            String password = "MySecurePassword123!";

            // When
            String hashedPassword = PasswordUtil.hashPassword(password);
            boolean matches = PasswordUtil.matches(password, hashedPassword);

            // Then
            assertTrue(matches, "加密后验证应成功");
        }

        @Test
        @DisplayName("多次加密后验证")
        void should_verifyMultipleHashes_successfully() {
            // Given
            String password = "MySecurePassword123!";

            // When & Then
            for (int i = 0; i < 10; i++) {
                String hashedPassword = PasswordUtil.hashPassword(password);
                assertTrue(PasswordUtil.matches(password, hashedPassword), 
                    "第" + (i + 1) + "次加密后验证应成功");
            }
        }
    }
}
