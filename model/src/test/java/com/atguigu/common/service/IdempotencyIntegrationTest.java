package com.atguigu.common.service;

import org.junit.jupiter.api.*;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幂等性服务集成测试类
 * 
 * 测试目的：
 * 1. 验证分布式锁的获取和释放
 * 2. 验证幂等性保证的正确性
 * 3. 验证并发场景下的正确性
 * 
 * 使用Testcontainers提供Redis容器化测试环境
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("幂等性服务集成测试")
class IdempotencyIntegrationTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("spring.cloud.sentinel.enabled", () -> "false");
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RedissonClient redissonClient;

    private static final String TEST_KEY_PREFIX = "test:idempotency:";

    // ==================== 基本锁操作测试 ====================

    @Nested
    @DisplayName("基本锁操作测试")
    class BasicLockTests {

        @Test
        @DisplayName("应该成功获取幂等性锁")
        void should_acquireLock_successfully() {
            // Given
            String key = TEST_KEY_PREFIX + "basic:lock";

            // When
            IdempotencyService.IdempotencyResult result = idempotencyService.tryLock(key);

            // Then
            assertTrue(result.isSuccess(), "应成功获取锁");
            assertNull(result.getFailureReason(), "失败原因应为空");

            // Cleanup
            idempotencyService.releaseLock(key);
        }

        @Test
        @DisplayName("应该成功释放幂等性锁")
        void should_releaseLock_successfully() {
            // Given
            String key = TEST_KEY_PREFIX + "release:lock";
            idempotencyService.tryLock(key);

            // When
            IdempotencyService.ReleaseResult result = idempotencyService.releaseLock(key);

            // Then
            assertTrue(result.isSuccess(), "应成功释放锁");
            assertEquals(IdempotencyService.ReleaseResult.ReleaseStatus.SUCCESS, result.getStatus());
        }

        @Test
        @DisplayName("应该阻止重复获取锁")
        void should_preventDuplicateLock() {
            // Given
            String key = TEST_KEY_PREFIX + "duplicate:lock";

            // When
            IdempotencyService.IdempotencyResult result1 = idempotencyService.tryLock(key);
            IdempotencyService.IdempotencyResult result2 = idempotencyService.tryLock(key);

            // Then
            assertTrue(result1.isSuccess(), "第一次获取锁应成功");
            assertTrue(result2.isFailure(), "第二次获取锁应失败");
            assertEquals(
                IdempotencyService.IdempotencyResult.FailureReason.DUPLICATE_REQUEST,
                result2.getFailureReason()
            );

            // Cleanup
            idempotencyService.releaseLock(key);
        }

        @Test
        @DisplayName("应该正确检查锁状态")
        void should_checkLockStatus_correctly() {
            // Given
            String key = TEST_KEY_PREFIX + "status:lock";

            // When - 获取锁前
            boolean beforeLock = idempotencyService.isLocked(key);

            // Then
            assertFalse(beforeLock, "获取锁前应未锁定");

            // When - 获取锁后
            idempotencyService.tryLock(key);
            boolean afterLock = idempotencyService.isLocked(key);

            // Then
            assertTrue(afterLock, "获取锁后应已锁定");

            // Cleanup
            idempotencyService.releaseLock(key);
        }
    }

    // ==================== 锁过期测试 ====================

    @Nested
    @DisplayName("锁过期测试")
    class LockExpiryTests {

        @Test
        @DisplayName("应该正确设置锁过期时间")
        void should_setLockExpiry_correctly() throws InterruptedException {
            // Given
            String key = TEST_KEY_PREFIX + "expiry:lock";
            long expireSeconds = 2;

            // When
            IdempotencyService.IdempotencyResult result = idempotencyService.tryLock(key, expireSeconds);

            // Then
            assertTrue(result.isSuccess(), "应成功获取锁");

            // 验证锁存在
            assertTrue(idempotencyService.isLocked(key), "锁应存在");

            // 等待锁过期
            Thread.sleep((expireSeconds + 1) * 1000);

            // 验证锁已过期
            assertFalse(idempotencyService.isLocked(key), "锁应已过期");
        }

        @Test
        @DisplayName("应该获取锁的剩余过期时间")
        void should_getRemainingTimeToLive_correctly() {
            // Given
            String key = TEST_KEY_PREFIX + "ttl:lock";
            long expireSeconds = 10;
            idempotencyService.tryLock(key, expireSeconds);

            // When
            long remainingTime = idempotencyService.getRemainingTimeToLive(key);

            // Then
            assertTrue(remainingTime > 0, "剩余时间应大于0");
            assertTrue(remainingTime <= expireSeconds * 1000, "剩余时间应不超过设置的时间");

            // Cleanup
            idempotencyService.releaseLock(key);
        }
    }

    // ==================== 带等待时间的锁测试 ====================

    @Nested
    @DisplayName("带等待时间的锁测试")
    class LockWithWaitTests {

        @Test
        @DisplayName("应该在等待时间内获取锁")
        void should_acquireLockWithinWaitTime() throws InterruptedException {
            // Given
            String key = TEST_KEY_PREFIX + "wait:lock";
            long waitSeconds = 2;
            long expireSeconds = 5;

            // When
            IdempotencyService.IdempotencyResult result = 
                idempotencyService.tryLockWithWait(key, waitSeconds, expireSeconds);

            // Then
            assertTrue(result.isSuccess(), "应在等待时间内获取锁");

            // Cleanup
            idempotencyService.releaseLock(key);
        }

        @Test
        @DisplayName("应该在等待超时后返回失败")
        void should_returnFailureAfterWaitTimeout() throws InterruptedException {
            // Given
            String key = TEST_KEY_PREFIX + "timeout:lock";
            
            // 先获取锁
            idempotencyService.tryLock(key, 10);

            // When - 尝试在短时间内获取同一个锁
            IdempotencyService.IdempotencyResult result = 
                idempotencyService.tryLockWithWait(key, 1, 10);

            // Then
            assertTrue(result.isFailure(), "应获取锁失败");
            assertEquals(
                IdempotencyService.IdempotencyResult.FailureReason.TIMEOUT,
                result.getFailureReason()
            );

            // Cleanup
            idempotencyService.releaseLock(key);
        }
    }

    // ==================== 并发测试 ====================

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("应该正确处理并发锁竞争")
        void should_handleConcurrentLockContention() throws InterruptedException {
            // Given
            String key = TEST_KEY_PREFIX + "concurrent:lock";
            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // When
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        IdempotencyService.IdempotencyResult result = idempotencyService.tryLock(key);
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                            // 模拟业务处理
                            Thread.sleep(100);
                            idempotencyService.releaseLock(key);
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // Then
            assertTrue(successCount.get() >= 1, "至少有一个线程应成功获取锁");
            assertEquals(threadCount, successCount.get() + failCount.get(), "所有线程应完成");
        }

        @Test
        @DisplayName("应该正确处理高频锁操作")
        void should_handleHighFrequencyLockOperations() throws InterruptedException {
            // Given
            int operationCount = 100;
            ExecutorService executorService = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(operationCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // When
            for (int i = 0; i < operationCount; i++) {
                final int index = i;
                executorService.submit(() -> {
                    try {
                        String key = TEST_KEY_PREFIX + "highfreq:lock:" + index;
                        IdempotencyService.IdempotencyResult result = idempotencyService.tryLock(key);
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                            idempotencyService.releaseLock(key);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executorService.shutdown();

            // Then
            assertEquals(operationCount, successCount.get(), "所有操作应成功");
        }
    }

    // ==================== 统计功能测试 ====================

    @Nested
    @DisplayName("统计功能测试")
    class StatisticsTests {

        @Test
        @DisplayName("应该正确统计锁操作")
        void should_trackLockStatistics_correctly() {
            // Given
            String key = TEST_KEY_PREFIX + "stats:lock";
            
            // 重置统计
            idempotencyService.resetStatistics();

            // When
            idempotencyService.tryLock(key);
            idempotencyService.tryLock(key); // 失败
            idempotencyService.releaseLock(key);

            // Then
            IdempotencyService.LockStatistics stats = idempotencyService.getStatistics();
            assertEquals(1, stats.getSuccessCount(), "成功次数应为1");
            assertEquals(1, stats.getFailureCount(), "失败次数应为1");
            assertEquals(1, stats.getReleaseCount(), "释放次数应为1");
        }

        @Test
        @DisplayName("应该正确计算失败率")
        void should_calculateFailureRate_correctly() {
            // Given
            idempotencyService.resetStatistics();
            String key = TEST_KEY_PREFIX + "rate:lock";

            // When
            idempotencyService.tryLock(key);
            idempotencyService.tryLock(key); // 失败
            idempotencyService.tryLock(key); // 失败
            idempotencyService.releaseLock(key);

            // Then
            IdempotencyService.LockStatistics stats = idempotencyService.getStatistics();
            assertEquals(1.0/3.0, stats.getFailureRate(), 0.01, "失败率应约为33%");
        }
    }

    // ==================== 异常处理测试 ====================

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("应该拒绝空key")
        void should_rejectNullKey() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                idempotencyService.tryLock(null);
            });
        }

        @Test
        @DisplayName("应该拒绝空字符串key")
        void should_rejectEmptyKey() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                idempotencyService.tryLock("");
            });
        }

        @Test
        @DisplayName("应该拒绝过长的key")
        void should_rejectTooLongKey() {
            // Given
            StringBuilder longKey = new StringBuilder();
            for (int i = 0; i < 300; i++) {
                longKey.append("a");
            }

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                idempotencyService.tryLock(longKey.toString());
            });
        }

        @Test
        @DisplayName("应该拒绝无效的过期时间")
        void should_rejectInvalidExpireTime() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                idempotencyService.tryLock("test-key", 0);
            });
        }
    }

    // ==================== 强制释放锁测试 ====================

    @Nested
    @DisplayName("强制释放锁测试")
    class ForceReleaseTests {

        @Test
        @DisplayName("应该成功强制释放锁")
        void should_forceReleaseLock_successfully() {
            // Given
            String key = TEST_KEY_PREFIX + "force:lock";
            idempotencyService.tryLock(key);

            // When
            IdempotencyService.ReleaseResult result = idempotencyService.forceReleaseLock(key);

            // Then
            assertTrue(result.isSuccess(), "强制释放应成功");
            assertFalse(idempotencyService.isLocked(key), "锁应已被释放");
        }

        @Test
        @DisplayName("应该正确处理不存在的锁")
        void should_handleNonExistentLock_correctly() {
            // Given
            String key = TEST_KEY_PREFIX + "nonexistent:lock";

            // When
            IdempotencyService.ReleaseResult result = idempotencyService.forceReleaseLock(key);

            // Then
            assertFalse(result.isSuccess(), "释放不存在的锁应失败");
            assertEquals(
                IdempotencyService.ReleaseResult.ReleaseStatus.NOT_HELD,
                result.getStatus()
            );
        }
    }
}
