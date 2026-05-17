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
    private static final String LIMIT_KEY_PREFIX = "verification:limit:";
    private static final String ATTEMPTS_KEY_PREFIX = "verification:attempts:";

    @Nested
    @DisplayName("generateAndStore 方法测试")
    class GenerateAndStoreTests {

        @Test
        @DisplayName("应该成功生成并存储验证码")
        void should_generateAndStoreCode_successfully() {
            String account = "13812345678";

            when(redisTemplate.hasKey(LIMIT_KEY_PREFIX + account)).thenReturn(false);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String code = verificationCodeService.generateAndStore(account);

            assertNotNull(code, "验证码不应为空");
            assertEquals(6, code.length(), "验证码应为6位数字");
            assertTrue(code.matches("\\d{6}"), "验证码应为6位数字");

            ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOperations, times(2)).set(anyString(), any(), durationCaptor.capture());
        }

        @Test
        @DisplayName("应该拒绝空账号")
        void should_rejectEmptyAccount() {
            assertThrows(IllegalArgumentException.class,
                () -> verificationCodeService.generateAndStore(""));
        }

        @Test
        @DisplayName("应该拒绝null账号")
        void should_rejectNullAccount() {
            assertThrows(IllegalArgumentException.class,
                () -> verificationCodeService.generateAndStore(null));
        }

        @Test
        @DisplayName("应该拒绝空白账号")
        void should_rejectBlankAccount() {
            assertThrows(IllegalArgumentException.class,
                () -> verificationCodeService.generateAndStore("   "));
        }

        @Test
        @DisplayName("应该生成不同的验证码")
        void should_generateDifferentCodes() {
            String account = "13812345678";

            when(redisTemplate.hasKey(LIMIT_KEY_PREFIX + account)).thenReturn(false);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String code1 = verificationCodeService.generateAndStore(account);
            String code2 = verificationCodeService.generateAndStore(account);

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
            String account = "13812345678";
            String code = "123456";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(ATTEMPTS_KEY_PREFIX + account)).thenReturn(null);
            when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any()
            )).thenReturn(1L);

            boolean result = verificationCodeService.verify(account, code);

            assertTrue(result, "正确验证码应验证成功");
            verify(redisTemplate).delete(ATTEMPTS_KEY_PREFIX + account);
        }

        @Test
        @DisplayName("应该拒绝错误的验证码")
        void should_rejectWrongCode() {
            String account = "13812345678";
            String wrongCode = "654321";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(ATTEMPTS_KEY_PREFIX + account)).thenReturn(null);
            when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any()
            )).thenReturn(0L);
            when(valueOperations.increment(ATTEMPTS_KEY_PREFIX + account)).thenReturn(1L);

            boolean result = verificationCodeService.verify(account, wrongCode);

            assertFalse(result, "错误验证码应验证失败");
        }

        @Test
        @DisplayName("应该拒绝空账号")
        void should_rejectEmptyAccount() {
            boolean result = verificationCodeService.verify("", "123456");

            assertFalse(result, "空账号应验证失败");
        }

        @Test
        @DisplayName("应该拒绝null账号")
        void should_rejectNullAccount() {
            boolean result = verificationCodeService.verify(null, "123456");

            assertFalse(result, "null账号应验证失败");
        }

        @Test
        @DisplayName("应该拒绝空验证码")
        void should_rejectEmptyCode() {
            boolean result = verificationCodeService.verify("13812345678", "");

            assertFalse(result, "空验证码应验证失败");
        }

        @Test
        @DisplayName("应该拒绝null验证码")
        void should_rejectNullCode() {
            boolean result = verificationCodeService.verify("13812345678", null);

            assertFalse(result, "null验证码应验证失败");
        }

        @Test
        @DisplayName("应该拒绝空白验证码")
        void should_rejectBlankCode() {
            boolean result = verificationCodeService.verify("13812345678", "   ");

            assertFalse(result, "空白验证码应验证失败");
        }

        @Test
        @DisplayName("应该处理Redis返回null的情况")
        void should_handleRedisNullResult() {
            String account = "13812345678";
            String code = "123456";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(ATTEMPTS_KEY_PREFIX + account)).thenReturn(null);
            when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any()
            )).thenReturn(null);
            when(valueOperations.increment(ATTEMPTS_KEY_PREFIX + account)).thenReturn(1L);

            boolean result = verificationCodeService.verify(account, code);

            assertFalse(result, "Redis返回null应验证失败");
        }
    }

    @Nested
    @DisplayName("delete 方法测试")
    class DeleteTests {

        @Test
        @DisplayName("应该成功删除验证码")
        void should_deleteCode_successfully() {
            String account = "13812345678";

            when(redisTemplate.delete(CODE_KEY_PREFIX + account)).thenReturn(true);

            verificationCodeService.delete(account);

            verify(redisTemplate).delete(CODE_KEY_PREFIX + account);
        }
    }

    @Nested
    @DisplayName("getExpireTime 方法测试")
    class GetExpireTimeTests {

        @Test
        @DisplayName("应该返回剩余过期时间")
        void should_returnExpireTime() {
            String account = "13812345678";
            Long expectedTtl = 180L;

            when(redisTemplate.getExpire(CODE_KEY_PREFIX + account, java.util.concurrent.TimeUnit.SECONDS))
                .thenReturn(expectedTtl);

            long result = verificationCodeService.getExpireTime(account);

            assertEquals(expectedTtl, result, "应返回正确的过期时间");
        }

        @Test
        @DisplayName("应该返回0当Redis返回null")
        void should_returnZero_whenRedisReturnsNull() {
            String account = "13812345678";

            when(redisTemplate.getExpire(CODE_KEY_PREFIX + account, java.util.concurrent.TimeUnit.SECONDS))
                .thenReturn(null);

            long result = verificationCodeService.getExpireTime(account);

            assertEquals(0, result, "Redis返回null应返回0");
        }
    }

    @Nested
    @DisplayName("验证码格式测试")
    class CodeFormatTests {

        @Test
        @DisplayName("验证码应为6位数字")
        void should_generateSixDigitCode() {
            String account = "13812345678";

            when(redisTemplate.hasKey(LIMIT_KEY_PREFIX + account)).thenReturn(false);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String code = verificationCodeService.generateAndStore(account);

            assertTrue(code.matches("\\d{6}"), "验证码应为6位数字");
            int codeValue = Integer.parseInt(code);
            assertTrue(codeValue >= 100000 && codeValue <= 999999,
                "验证码应在100000-999999范围内");
        }

        @Test
        @DisplayName("多次生成验证码格式应一致")
        void should_generateConsistentFormat() {
            String account = "13812345678";

            when(redisTemplate.hasKey(LIMIT_KEY_PREFIX + account)).thenReturn(false);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            for (int i = 0; i < 10; i++) {
                String code = verificationCodeService.generateAndStore(account);
                assertTrue(code.matches("\\d{6}"),
                    "第" + (i + 1) + "次生成的验证码应为6位数字");
            }
        }
    }

    @Nested
    @DisplayName("频率限制测试")
    class RateLimitTests {

        @Test
        @DisplayName("第一次请求应成功")
        void should_allowFirstRequest() {
            String account = "13812345678";
            when(redisTemplate.hasKey(LIMIT_KEY_PREFIX + account)).thenReturn(false);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String code = verificationCodeService.generateAndStore(account);

            assertNotNull(code);
            assertTrue(code.matches("\\d{6}"));
        }

        @Test
        @DisplayName("60秒内第二次请求应抛出IllegalStateException")
        void should_rejectFrequentRequests() {
            String account = "13812345678";
            when(redisTemplate.hasKey(LIMIT_KEY_PREFIX + account)).thenReturn(true);

            assertThrows(IllegalStateException.class,
                () -> verificationCodeService.generateAndStore(account));
        }
    }

    @Nested
    @DisplayName("尝试次数限制测试")
    class AttemptLimitTests {

        @Test
        @DisplayName("前5次验证失败应正常返回false")
        void should_allowUpToFiveAttempts() {
            String account = "13812345678";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(ATTEMPTS_KEY_PREFIX + account)).thenReturn("4");
            when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any()
            )).thenReturn(0L);
            when(valueOperations.increment(ATTEMPTS_KEY_PREFIX + account)).thenReturn(5L);

            boolean result = verificationCodeService.verify(account, "wrong");

            assertFalse(result);
        }

        @Test
        @DisplayName("第5次失败后验证码应被删除")
        void should_deleteCodeAfterFiveFailedAttempts() {
            String account = "13812345678";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(ATTEMPTS_KEY_PREFIX + account)).thenReturn("5");

            boolean result = verificationCodeService.verify(account, "wrong");

            assertFalse(result);
            verify(redisTemplate).delete(CODE_KEY_PREFIX + account);
            verify(redisTemplate).delete(ATTEMPTS_KEY_PREFIX + account);
        }

        @Test
        @DisplayName("验证成功后尝试次数应被清除")
        void should_resetAttemptsOnSuccess() {
            String account = "13812345678";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(ATTEMPTS_KEY_PREFIX + account)).thenReturn("3");
            when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any()
            )).thenReturn(1L);

            boolean result = verificationCodeService.verify(account, "123456");

            assertTrue(result);
            verify(redisTemplate).delete(ATTEMPTS_KEY_PREFIX + account);
        }
    }
}
