package com.atguigu.common.service;

import com.atguigu.common.EmbeddedRedisInitializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.atguigu.common.TestConfig.class)
@ContextConfiguration(initializers = EmbeddedRedisInitializer.class)
@ActiveProfiles("test")
@DisplayName("幂等性服务集成测试")
class IdempotencyIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private static final String TEST_KEY_PREFIX = "test:idempotency:";

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(stringRedisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("基本锁操作测试")
    class BasicLockTests {

        @Test
        @DisplayName("应该成功获取幂等性锁")
        void should_acquireLock_successfully() {
            String key = TEST_KEY_PREFIX + "basic:lock";

            IdempotencyService.IdempotencyResult result = idempotencyService.tryLock(key);

            assertTrue(result.isSuccess(), "应成功获取锁");
            assertNull(result.getFailureReason(), "失败原因应为空");

            idempotencyService.releaseLock(key);
        }

        @Test
        @DisplayName("应该成功释放幂等性锁")
        void should_releaseLock_successfully() {
            String key = TEST_KEY_PREFIX + "release:lock";
            idempotencyService.tryLock(key);

            IdempotencyService.ReleaseResult result = idempotencyService.releaseLock(key);

            assertTrue(result.isSuccess(), "应成功释放锁");
            assertEquals(IdempotencyService.ReleaseResult.ReleaseStatus.SUCCESS, result.getStatus());
        }

        @Test
        @DisplayName("应该阻止重复获取锁")
        void should_preventDuplicateLock() {
            String key = TEST_KEY_PREFIX + "duplicate:lock";

            IdempotencyService.IdempotencyResult result1 = idempotencyService.tryLock(key);
            IdempotencyService.IdempotencyResult result2 = idempotencyService.tryLock(key);

            assertTrue(result1.isSuccess(), "第一次获取锁应成功");
            assertTrue(result2.isFailure(), "第二次获取锁应失败");
            assertEquals(
                IdempotencyService.IdempotencyResult.FailureReason.DUPLICATE_REQUEST,
                result2.getFailureReason()
            );

            idempotencyService.releaseLock(key);
        }

        @Test
        @DisplayName("应该正确检查锁状态")
        void should_checkLockStatus_correctly() {
            String key = TEST_KEY_PREFIX + "status:lock";

            boolean beforeLock = idempotencyService.isLocked(key);

            assertFalse(beforeLock, "获取锁前应未锁定");

            idempotencyService.tryLock(key);
            boolean afterLock = idempotencyService.isLocked(key);

            assertTrue(afterLock, "获取锁后应已锁定");

            idempotencyService.releaseLock(key);
        }
    }

    @Nested
    @DisplayName("锁过期测试")
    class LockExpiryTests {

        @Test
        @DisplayName("应该正确设置锁过期时间")
        void should_setLockExpiry_correctly() throws InterruptedException {
            String key = TEST_KEY_PREFIX + "expiry:lock";
            long expireSeconds = 2;

            IdempotencyService.IdempotencyResult result = idempotencyService.tryLock(key, expireSeconds);

            assertTrue(result.isSuccess(), "应成功获取锁");
            assertTrue(idempotencyService.isLocked(key), "锁应存在");

            Thread.sleep((expireSeconds + 1) * 1000);

            assertFalse(idempotencyService.isLocked(key), "锁应已过期");
        }

        @Test
        @DisplayName("应该获取锁的剩余过期时间")
        void should_getRemainingTimeToLive_correctly() {
            String key = TEST_KEY_PREFIX + "ttl:lock";
            long expireSeconds = 10;
            idempotencyService.tryLock(key, expireSeconds);

            long remainingTime = idempotencyService.getRemainingTimeToLive(key);

            assertTrue(remainingTime > 0, "剩余时间应大于0");
            assertTrue(remainingTime <= expireSeconds * 1000, "剩余时间应不超过设置的时间");

            idempotencyService.releaseLock(key);
        }
    }

    @Nested
    @DisplayName("带等待时间的锁测试")
    class LockWithWaitTests {

        @Test
        @DisplayName("应该在等待时间内获取锁")
        void should_acquireLockWithinWaitTime() {
            String key = TEST_KEY_PREFIX + "wait:lock";
            long waitSeconds = 2;
            long expireSeconds = 5;

            IdempotencyService.IdempotencyResult result =
                idempotencyService.tryLockWithWait(key, waitSeconds, expireSeconds);

            assertTrue(result.isSuccess(), "应在等待时间内获取锁");

            idempotencyService.releaseLock(key);
        }

        @Test
        @DisplayName("应该在等待超时后返回失败")
        void should_returnFailureAfterWaitTimeout() {
            String key = TEST_KEY_PREFIX + "timeout:lock";

            idempotencyService.tryLock(key, 10);

            IdempotencyService.IdempotencyResult result =
                idempotencyService.tryLockWithWait(key, 1, 10);

            assertTrue(result.isFailure(), "应获取锁失败");
            assertEquals(
                IdempotencyService.IdempotencyResult.FailureReason.TIMEOUT,
                result.getFailureReason()
            );

            idempotencyService.releaseLock(key);
        }
    }

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("应该正确处理并发锁竞争")
        void should_handleConcurrentLockContention() throws InterruptedException {
            String key = TEST_KEY_PREFIX + "concurrent:lock";
            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        IdempotencyService.IdempotencyResult result = idempotencyService.tryLock(key);
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
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
            boolean allDone = endLatch.await(10, TimeUnit.SECONDS);
            if (!allDone) {
                System.out.println("线程未全部执行完，超时了！");
            }
            executorService.shutdown();

            assertTrue(successCount.get() >= 1, "至少有一个线程应成功获取锁");
            assertEquals(threadCount, successCount.get() + failCount.get(), "所有线程应完成");
        }

        @Test
        @DisplayName("应该正确处理高频锁操作")
        void should_handleHighFrequencyLockOperations() throws InterruptedException {
            int operationCount = 100;
            ExecutorService executorService = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(operationCount);
            AtomicInteger successCount = new AtomicInteger(0);

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

            boolean allDone = latch.await(30, TimeUnit.SECONDS);
            if (!allDone) {
                System.out.println("线程未全部执行完，超时了！");
            }
            executorService.shutdown();

            assertEquals(operationCount, successCount.get(), "所有操作应成功");
        }
    }

    @Nested
    @DisplayName("统计功能测试")
    class StatisticsTests {

        @Test
        @DisplayName("应该正确统计锁操作")
        void should_trackLockStatistics_correctly() {
            String key = TEST_KEY_PREFIX + "stats:lock";

            idempotencyService.resetStatistics();

            idempotencyService.tryLock(key);
            idempotencyService.tryLock(key);
            idempotencyService.releaseLock(key);

            IdempotencyService.LockStatistics stats = idempotencyService.getStatistics();
            assertEquals(1, stats.successCount(), "成功次数应为1");
            assertEquals(1, stats.failureCount(), "失败次数应为1");
            assertEquals(1, stats.releaseCount(), "释放次数应为1");
        }

        @Test
        @DisplayName("应该正确计算失败率")
        void should_calculateFailureRate_correctly() {
            idempotencyService.resetStatistics();
            String key = TEST_KEY_PREFIX + "rate:lock";

            idempotencyService.tryLock(key);
            idempotencyService.tryLock(key);
            idempotencyService.tryLock(key);
            idempotencyService.releaseLock(key);

            IdempotencyService.LockStatistics stats = idempotencyService.getStatistics();
            assertEquals(2.0/3.0, stats.getFailureRate(), 0.01, "失败率应约为66.7%");
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("应该拒绝空key")
        void should_rejectNullKey() {
            assertThrows(IllegalArgumentException.class, () -> idempotencyService.tryLock(null));
        }

        @Test
        @DisplayName("应该拒绝空字符串key")
        void should_rejectEmptyKey() {
            assertThrows(IllegalArgumentException.class, () -> idempotencyService.tryLock(""));
        }

        @Test
        @DisplayName("应该拒绝过长的key")
        void should_rejectTooLongKey() {

            assertThrows(IllegalArgumentException.class, () -> idempotencyService.tryLock("a".repeat(300)));
        }

        @Test
        @DisplayName("应该拒绝无效的过期时间")
        void should_rejectInvalidExpireTime() {
            assertThrows(IllegalArgumentException.class, () -> idempotencyService.tryLock("test-key", 0));
        }
    }

    @Nested
    @DisplayName("强制释放锁测试")
    class ForceReleaseTests {

        @Test
        @DisplayName("应该成功强制释放锁")
        void should_forceReleaseLock_successfully() {
            String key = TEST_KEY_PREFIX + "force:lock";
            idempotencyService.tryLock(key);

            IdempotencyService.ReleaseResult result = idempotencyService.forceReleaseLock(key);

            assertTrue(result.isSuccess(), "强制释放应成功");
            assertFalse(idempotencyService.isLocked(key), "锁应已被释放");
        }

        @Test
        @DisplayName("应该正确处理不存在的锁")
        void should_handleNonExistentLock_correctly() {
            String key = TEST_KEY_PREFIX + "nonexistent:lock";

            IdempotencyService.ReleaseResult result = idempotencyService.forceReleaseLock(key);

            assertFalse(result.isSuccess(), "释放不存在的锁应失败");
            assertEquals(
                IdempotencyService.ReleaseResult.ReleaseStatus.NOT_HELD,
                result.getStatus()
            );
        }
    }
}
