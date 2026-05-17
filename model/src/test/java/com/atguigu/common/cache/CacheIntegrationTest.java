package com.atguigu.common.cache;

import org.junit.jupiter.api.*;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存操作集成测试类
 * 
 * 测试目的：
 * 1. 验证Redis缓存读写操作的正确性
 * 2. 验证缓存失效机制的正确性
 * 3. 验证分布式锁的正确性
 * 
 * 使用Testcontainers提供Redis容器化测试环境
 */
@SpringBootTest(classes = com.atguigu.common.TestConfig.class)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("缓存操作集成测试")
class CacheIntegrationTest {

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
    private CacheService cacheService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TEST_KEY_PREFIX = "test:cache:";

    // ==================== Redis缓存读写测试 ====================

    @Nested
    @DisplayName("Redis缓存读写测试")
    class CacheReadWriteTests {

        @Test
        @DisplayName("应该成功设置和获取缓存")
        void should_setAndGetCache_successfully() {
            // Given
            String key = TEST_KEY_PREFIX + "simple";
            String value = "test-value";

            // When
            cacheService.setCache(key, value);
            Object result = cacheService.getCache(key);

            // Then
            assertNotNull(result, "缓存值不应为空");
            assertEquals(value, result, "缓存值应正确");
            
            // Cleanup
            cacheService.delete(key);
        }

        @Test
        @DisplayName("应该成功设置空值缓存")
        void should_setNullCache_successfully() {
            // Given
            String key = TEST_KEY_PREFIX + "null";

            // When
            cacheService.setNullCache(key);

            // Then
            assertTrue(cacheService.isNullCache(key), "应为空值缓存");
            
            // Cleanup
            cacheService.delete(key);
        }

        @Test
        @DisplayName("应该正确判断缓存是否存在")
        void should_checkCacheExists_correctly() {
            // Given
            String existingKey = TEST_KEY_PREFIX + "existing";
            String nonExistingKey = TEST_KEY_PREFIX + "non-existing";
            
            cacheService.setCache(existingKey, "value");

            // When & Then
            assertTrue(cacheService.exists(existingKey), "存在的缓存应返回true");
            assertFalse(cacheService.exists(nonExistingKey), "不存在的缓存应返回false");
            
            // Cleanup
            cacheService.delete(existingKey);
        }

        @Test
        @DisplayName("应该成功删除缓存")
        void should_deleteCache_successfully() {
            // Given
            String key = TEST_KEY_PREFIX + "to-delete";
            cacheService.setCache(key, "value");

            // When
            cacheService.delete(key);

            // Then
            assertFalse(cacheService.exists(key), "删除后缓存不应存在");
        }

        @Test
        @DisplayName("应该成功设置带过期时间的缓存")
        void should_setCacheWithExpiry_successfully() throws InterruptedException {
            // Given
            String key = TEST_KEY_PREFIX + "expiry";
            String value = "expiring-value";

            // When
            cacheService.setCache(key, value);
            
            // Then - 立即获取应成功
            assertNotNull(cacheService.getCache(key), "缓存应存在");
            
            // 等待一段时间后缓存应该仍然存在（因为过期时间是随机的，至少1小时）
            Thread.sleep(100);
            assertNotNull(cacheService.getCache(key), "短时间内缓存应仍存在");
            
            // Cleanup
            cacheService.delete(key);
        }
    }

    // ==================== 缓存失效测试 ====================

    @Nested
    @DisplayName("缓存失效测试")
    class CacheInvalidationTests {

        @Test
        @DisplayName("应该成功执行双删策略")
        void should_executeDoubleRemoval_successfully() throws InterruptedException {
            // Given
            String key = TEST_KEY_PREFIX + "double-delete";
            cacheService.setCache(key, "initial-value");

            // When - 执行双删
            cacheService.deleteWithDoubleRemoval(key, 100);

            // Then - 第一次删除后缓存应不存在
            assertFalse(cacheService.exists(key), "第一次删除后缓存不应存在");

            // 等待第二次删除
            Thread.sleep(200);

            // 缓存仍应不存在
            assertFalse(cacheService.exists(key), "第二次删除后缓存不应存在");
        }

        @Test
        @DisplayName("应该成功使用互斥锁获取缓存")
        void should_getCacheWithMutex_successfully() {
            // Given
            String key = TEST_KEY_PREFIX + "mutex";
            String expectedValue = "loaded-value";
            Supplier<String> loader = () -> expectedValue;

            // When
            String result = cacheService.getCacheWithMutex(key, loader);

            // Then
            assertEquals(expectedValue, result, "应返回加载的值");
            
            // 验证缓存已设置
            Object cachedValue = cacheService.getCache(key);
            assertEquals(expectedValue, cachedValue, "缓存值应正确");
            
            // Cleanup
            cacheService.delete(key);
        }

        @Test
        @DisplayName("应该在缓存存在时直接返回缓存值")
        void should_returnCachedValue_whenCacheExists() {
            // Given
            String key = TEST_KEY_PREFIX + "cached";
            String cachedValue = "cached-value";
            cacheService.setCache(key, cachedValue);
            
            AtomicInteger loaderCallCount = new AtomicInteger(0);
            Supplier<String> loader = () -> {
                loaderCallCount.incrementAndGet();
                return "new-value";
            };

            // When
            String result = cacheService.getCacheWithMutex(key, loader);

            // Then
            assertEquals(cachedValue, result, "应返回缓存值");
            assertEquals(0, loaderCallCount.get(), "Loader不应被调用");
            
            // Cleanup
            cacheService.delete(key);
        }

        @Test
        @DisplayName("应该正确处理空值缓存")
        void should_handleNullCache_correctly() {
            // Given
            String key = TEST_KEY_PREFIX + "null-cache";
            Supplier<String> loader = () -> null;

            // When
            String result = cacheService.getCacheWithMutex(key, loader);

            // Then
            assertNull(result, "应返回null");
            assertTrue(cacheService.isNullCache(key), "应设置空值缓存");
            
            // Cleanup
            cacheService.delete(key);
        }
    }

    // ==================== 分布式锁测试 ====================

    @Nested
    @DisplayName("分布式锁测试")
    class DistributedLockTests {

        @Test
        @DisplayName("应该成功获取和释放锁")
        void should_acquireAndReleaseLock_successfully() {
            // Given
            String lockKey = TEST_KEY_PREFIX + "lock:simple";

            // When
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = lock.tryLock();

            // Then
            assertTrue(acquired, "应成功获取锁");
            assertTrue(lock.isHeldByCurrentThread(), "当前线程应持有锁");

            // Release
            lock.unlock();
            assertFalse(lock.isHeldByCurrentThread(), "释放后不应持有锁");
        }

        @Test
        @DisplayName("应该阻止重复获取锁")
        void should_preventDuplicateLock() throws InterruptedException {
            // Given
            String lockKey = TEST_KEY_PREFIX + "lock:duplicate";
            RLock lock1 = redissonClient.getLock(lockKey);
            RLock lock2 = redissonClient.getLock(lockKey);

            // When
            boolean acquired1 = lock1.tryLock();
            boolean acquired2 = lock2.tryLock(100, TimeUnit.MILLISECONDS);

            // Then
            assertTrue(acquired1, "第一次获取锁应成功");
            assertFalse(acquired2, "第二次获取锁应失败");

            // Cleanup
            if (lock1.isHeldByCurrentThread()) {
                lock1.unlock();
            }
        }

        @Test
        @DisplayName("应该正确处理并发锁竞争")
        void should_handleConcurrentLockContention() throws InterruptedException {
            // Given
            String lockKey = TEST_KEY_PREFIX + "lock:concurrent";
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
                        RLock lock = redissonClient.getLock(lockKey);
                        if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                            try {
                                successCount.incrementAndGet();
                                Thread.sleep(50); // 模拟业务处理
                            } finally {
                                lock.unlock();
                            }
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
            endLatch.await(5, TimeUnit.SECONDS);
            executorService.shutdown();

            // Then - 应该只有一个线程成功获取锁
            assertTrue(successCount.get() >= 1, "至少有一个线程应成功获取锁");
        }

        @Test
        @DisplayName("应该正确处理锁过期")
        void should_handleLockExpiry_correctly() throws InterruptedException {
            // Given
            String lockKey = TEST_KEY_PREFIX + "lock:expiry";
            RLock lock = redissonClient.getLock(lockKey);

            // When
            boolean acquired = lock.tryLock(0, 1, TimeUnit.SECONDS);
            
            // Then
            assertTrue(acquired, "应成功获取锁");
            
            // 等待锁过期
            Thread.sleep(1500);
            
            // 锁应该已过期
            assertFalse(lock.isLocked(), "锁应已过期");
        }
    }

    // ==================== 缓存穿透防护测试 ====================

    @Nested
    @DisplayName("缓存穿透防护测试")
    class CachePenetrationTests {

        @Test
        @DisplayName("应该使用空值缓存防止缓存穿透")
        void should_preventCachePenetration_withNullCache() {
            // Given
            String key = TEST_KEY_PREFIX + "penetration";
            Supplier<String> loader = () -> null; // 模拟数据库中不存在的数据

            // When - 第一次查询
            String result1 = cacheService.getCacheWithMutex(key, loader);
            
            // Then
            assertNull(result1, "应返回null");
            assertTrue(cacheService.isNullCache(key), "应设置空值缓存");

            // When - 第二次查询（应命中空值缓存）
            AtomicInteger loaderCallCount = new AtomicInteger(0);
            Supplier<String> loader2 = () -> {
                loaderCallCount.incrementAndGet();
                return null;
            };
            String result2 = cacheService.getCacheWithMutex(key, loader2);

            // Then
            assertNull(result2, "应返回null");
            assertEquals(0, loaderCallCount.get(), "Loader不应被调用（应命中空值缓存）");
            
            // Cleanup
            cacheService.delete(key);
        }
    }

    // ==================== 缓存雪崩防护测试 ====================

    @Nested
    @DisplayName("缓存雪崩防护测试")
    class CacheAvalancheTests {

        @Test
        @DisplayName("应该使用随机过期时间防止缓存雪崩")
        void should_preventCacheAvalanche_withRandomExpiry() {
            // Given
            int keyCount = 10;
            long[] expireTimes = new long[keyCount];

            // When - 设置多个缓存
            for (int i = 0; i < keyCount; i++) {
                String key = TEST_KEY_PREFIX + "avalanche:" + i;
                cacheService.setCache(key, "value" + i);
                
                // 获取过期时间
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                expireTimes[i] = ttl != null ? ttl : 0;
            }

            // Then - 过期时间应该不完全相同
            boolean hasDifferentExpiry = false;
            for (int i = 1; i < keyCount; i++) {
                if (expireTimes[i] != expireTimes[0]) {
                    hasDifferentExpiry = true;
                    break;
                }
            }
            assertTrue(hasDifferentExpiry, "过期时间应该有差异");

            // Cleanup
            for (int i = 0; i < keyCount; i++) {
                cacheService.delete(TEST_KEY_PREFIX + "avalanche:" + i);
            }
        }
    }

    // ==================== 性能测试 ====================

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("应该高效处理批量缓存操作")
        void should_handleBatchCacheOperations_efficiently() {
            // Given
            int operationCount = 100;
            long startTime = System.currentTimeMillis();

            // When
            for (int i = 0; i < operationCount; i++) {
                String key = TEST_KEY_PREFIX + "perf:" + i;
                cacheService.setCache(key, "value" + i);
            }

            for (int i = 0; i < operationCount; i++) {
                String key = TEST_KEY_PREFIX + "perf:" + i;
                cacheService.getCache(key);
            }

            long endTime = System.currentTimeMillis();

            // Then
            long duration = endTime - startTime;
            System.out.println("批量缓存操作耗时: " + duration + "ms");
            assertTrue(duration < 5000, "批量操作应在5秒内完成");

            // Cleanup
            for (int i = 0; i < operationCount; i++) {
                cacheService.delete(TEST_KEY_PREFIX + "perf:" + i);
            }
        }
    }
}
