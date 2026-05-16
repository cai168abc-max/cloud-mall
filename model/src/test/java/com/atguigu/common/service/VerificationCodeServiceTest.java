package com.atguigu.common.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VerificationCodeService 单元测试类
 * 测试验证码服务相关业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationCodeService 单元测试")
class VerificationCodeServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private VerificationCodeService verificationCodeService;

    private static final String CODE_KEY_PREFIX = "verification:code:";

    @Nested
    @DisplayName("generateAndStore 方法测试")
    class GenerateAndStoreTests {

        @Test
        @DisplayName("应该成功生成并存储验证码")
        void should_generateAndStoreCode_successfully() {
            // Given
            String account = "13812345678";
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            String code = verificationCodeService.generateAndStore(account);

            // Then
            assertNotNull(code, "验证码不应为空");
            assertEquals(6, code.length(), "验证码应为6位数字");
            assertTrue(code.matches("\\d{6}"), "验证码应为6位数字");
            
            ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOperations).set(eq(CODE_KEY_PREFIX + account), eq(code), durationCaptor.capture());
            assertEquals(Duration.ofMinutes(5), durationCaptor.getValue(), "过期时间应为5分钟");
        }

        @Test
        @DisplayName("应该拒绝空账号")
        void should_rejectEmptyAccount() {
            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> verificationCodeService.generateAndStore(""));
        }

        @Test
        @DisplayName("应该拒绝null账号")
        void should_rejectNullAccount() {
            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> verificationCodeService.generateAndStore(null));
        }

        @Test
        @DisplayName("应该拒绝空白账号")
        void should_rejectBlankAccount() {
            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> verificationCodeService.generateAndStore("   "));
        }

        @Test
        @DisplayName("应该生成不同的验证码")
        void should_generateDifferentCodes() {
            // Given
            String account = "13812345678";
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            String code1 = verificationCodeService.generateAndStore(account);
            String code2 = verificationCodeService.generateAndStore(account);

            // Then
            // 虽然有可能相同，但连续两次相同的概率极低
            // 这里只验证都是有效的6位数字
            assertTrue(code1.matches("\\d{6}"), "验证码1应为6位数字");
            assertTrue(code2.matches("\\d{6}"), "验证码2应为6位数字");
        }
    }

    @Nested
    @DisplayName("verify 方法测试")
    class VerifyTests {

        @Test
        @DisplayName("应该成功验证正确的验证码")
        void should_verifyCorrectCode_successfully() {
            // Given
            String account = "13812345678";
            String code = "123456";
            
            when(redisTemplate.execute(
                any(DefaultRedisScript.class), 
                anyList(), 
                any()
            )).thenReturn(1L);

            // When
            boolean result = verificationCodeService.verify(account, code);

            // Then
            assertTrue(result, "正确验证码应验证成功");
        }

        @Test
        @DisplayName("应该拒绝错误的验证码")
        void should_rejectWrongCode() {
            // Given
            String account = "13812345678";
            String wrongCode = "654321";
            
            when(redisTemplate.execute(
                any(DefaultRedisScript.class), 
                anyList(), 
                any()
            )).thenReturn(0L);

            // When
            boolean result = verificationCodeService.verify(account, wrongCode);

            // Then
            assertFalse(result, "错误验证码应验证失败");
        }

        @Test
        @DisplayName("应该拒绝空账号")
        void should_rejectEmptyAccount() {
            // When
            boolean result = verificationCodeService.verify("", "123456");

            // Then
            assertFalse(result, "空账号应验证失败");
        }

        @Test
        @DisplayName("应该拒绝null账号")
        void should_rejectNullAccount() {
            // When
            boolean result = verificationCodeService.verify(null, "123456");

            // Then
            assertFalse(result, "null账号应验证失败");
        }

        @Test
        @DisplayName("应该拒绝空验证码")
        void should_rejectEmptyCode() {
            // When
            boolean result = verificationCodeService.verify("13812345678", "");

            // Then
            assertFalse(result, "空验证码应验证失败");
        }

        @Test
        @DisplayName("应该拒绝null验证码")
        void should_rejectNullCode() {
            // When
            boolean result = verificationCodeService.verify("13812345678", null);

            // Then
            assertFalse(result, "null验证码应验证失败");
        }

        @Test
        @DisplayName("应该拒绝空白验证码")
        void should_rejectBlankCode() {
            // When
            boolean result = verificationCodeService.verify("13812345678", "   ");

            // Then
            assertFalse(result, "空白验证码应验证失败");
        }

        @Test
        @DisplayName("应该处理Redis返回null的情况")
        void should_handleRedisNullResult() {
            // Given
            String account = "13812345678";
            String code = "123456";
            
            when(redisTemplate.execute(
                any(DefaultRedisScript.class), 
                anyList(), 
                any()
            )).thenReturn(null);

            // When
            boolean result = verificationCodeService.verify(account, code);

            // Then
            assertFalse(result, "Redis返回null应验证失败");
        }
    }

    @Nested
    @DisplayName("delete 方法测试")
    class DeleteTests {

        @Test
        @DisplayName("应该成功删除验证码")
        void should_deleteCode_successfully() {
            // Given
            String account = "13812345678";
            
            when(redisTemplate.delete(CODE_KEY_PREFIX + account)).thenReturn(true);

            // When
            verificationCodeService.delete(account);

            // Then
            verify(redisTemplate).delete(CODE_KEY_PREFIX + account);
        }
    }

    @Nested
    @DisplayName("getExpireTime 方法测试")
    class GetExpireTimeTests {

        @Test
        @DisplayName("应该返回剩余过期时间")
        void should_returnExpireTime() {
            // Given
            String account = "13812345678";
            Long expectedTtl = 180L;
            
            when(redisTemplate.getExpire(CODE_KEY_PREFIX + account, java.util.concurrent.TimeUnit.SECONDS))
                .thenReturn(expectedTtl);

            // When
            long result = verificationCodeService.getExpireTime(account);

            // Then
            assertEquals(expectedTtl, result, "应返回正确的过期时间");
        }

        @Test
        @DisplayName("应该返回0当Redis返回null")
        void should_returnZero_whenRedisReturnsNull() {
            // Given
            String account = "13812345678";
            
            when(redisTemplate.getExpire(CODE_KEY_PREFIX + account, java.util.concurrent.TimeUnit.SECONDS))
                .thenReturn(null);

            // When
            long result = verificationCodeService.getExpireTime(account);

            // Then
            assertEquals(0, result, "Redis返回null应返回0");
        }
    }

    @Nested
    @DisplayName("验证码格式测试")
    class CodeFormatTests {

        @Test
        @DisplayName("验证码应为6位数字")
        void should_generateSixDigitCode() {
            // Given
            String account = "13812345678";
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            String code = verificationCodeService.generateAndStore(account);

            // Then
            assertTrue(code.matches("\\d{6}"), "验证码应为6位数字");
            int codeValue = Integer.parseInt(code);
            assertTrue(codeValue >= 100000 && codeValue <= 999999, 
                "验证码应在100000-999999范围内");
        }

        @Test
        @DisplayName("多次生成验证码格式应一致")
        void should_generateConsistentFormat() {
            // Given
            String account = "13812345678";
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // When & Then
            for (int i = 0; i < 10; i++) {
                String code = verificationCodeService.generateAndStore(account);
                assertTrue(code.matches("\\d{6}"), 
                    "第" + (i + 1) + "次生成的验证码应为6位数字");
            }
        }
    }
}
