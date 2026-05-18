package com.atguigu.common.cache;

import com.atguigu.common.EmbeddedRedisInitializer;
import org.junit.jupiter.api.*;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.atguigu.common.TestConfig.class)
@ContextConfiguration(initializers = EmbeddedRedisInitializer.class)
@ActiveProfiles("test")
@DisplayName("缓存操作集成测试")
class CacheIntegrationTest {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private static final String TEST_KEY_PREFIX = "test:cache:";

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(stringRedisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("Redis缓存读写测试")
    class CacheReadWriteTests {

        @Test
        @DisplayName("应该成功设置和获取缓存")
        void should_setAndGetCache_successfully() {
            String key = TEST_KEY_PREFIX + "simple";
            String value = "test-value";

            cacheService.setCache(key, value);
            Object result = cacheService.getCache(key);

            assertNotNull(result, "缓存值不应为空");
            assertEquals(value, result, "缓存值应正确");

            cacheService.delete(key);
        }

        @Test
        @DisplayName("应该成功设置空值缓存")
        void should_setNullCache_successfully() {
            String key = TEST_KEY_PREFIX + "null";

            cacheService.setNullCache(key);

            assertTrue(cacheService.isNullCache(key), "应为空值缓存");

            cacheService.delete(key);
        }

        @Test
        @DisplayName("应该正确判断缓存是否存在")
        void should_checkCacheExists_correctly() {
            String existingKey = TEST_KEY_PREFIX + "existing";
            String nonExistingKey = TEST_KEY_PREFIX + "non-existing";

            cacheService.setCache(existingKey, "value");

            assertTrue(cacheService.exists(existingKey), "存在的缓存应返回true");
            assertFalse(cacheService.exists(nonExistingKey), "不存在的缓存应返回false");

            cacheService.delete(existingKey);
        }

        @Test
        @DisplayName("应该成功删除缓存")
        void should_deleteCache_successfully() {
            String key = TEST_KEY_PREFIX + "to-delete";
            cacheService.setCache(key, "value");

            cacheService.delete(key);

            assertFalse(cacheService.exists(key), "删除后缓存不应存在");
        }

        @Test
        @DisplayName("应该成功设置带过期时间的缓存")
        void should_setCacheWithExpiry_successfully() throws InterruptedException {
            String key = TEST_KEY_PREFIX + "expiry";
            String value = "expiring-value";

            cacheService.setCache(key, value);

            assertNotNull(cacheService.getCache(key), "缓存应存在");

            Thread.sleep(100);
            assertNotNull(cacheService.getCache(key), "短时间内缓存应仍存在");

            cacheService.delete(key);
        }
    }

    @Nested
    @DisplayName("缓存失效测试")
    class CacheInvalidationTests {

        @Test
        @DisplayName("应该成功执行双删策略")
        void should_executeDoubleRemoval_successfully() throws InterruptedException {
            String key = TEST_KEY_PREFIX + "double-delete";
            cacheService.setCache(key, "initial-value");

            cacheService.deleteWithDoubleRemoval(key, 100);

            assertFalse(cacheService.exists(key), "第一次删除后缓存不应存在");

            Thread.sleep(200);

            assertFalse(cacheService.exists(key), "第二次删除后缓存不应存在");
        }

        @Test
        @DisplayName("应该成功使用互斥锁获取缓存")
        void should_getCacheWithMutex_successfully() {
            String key = TEST_KEY_PREFIX + "mutex";
            String expectedValue = "loaded-value";
            Supplier<String> loader = () -> expectedValue;

            String result = cacheService.getCacheWithMutex(key, loader);

            assertEquals(expectedValue, result, "应返回加载的值");

            Object cachedValue = cacheService.getCache(key);
            assertEquals(expectedValue, cachedValue, "缓存值应正确");

            cacheService.delete(key);
        }

        @Test
        @DisplayName("应该在缓存存在时直接返回缓存值")
        void should_returnCachedValue_whenCacheExists() {
            String key = TEST_KEY_PREFIX + "cached";
            String cachedValue = "cached-value";
            cacheService.setCache(key, cachedValue);

            AtomicInteger loaderCallCount = new AtomicInteger(0);
            Supplier<String> loader = () -> {
                loaderCallCount.incrementAndGet();
                return "new-value";
            };

            String result = cacheService.getCacheWithMutex(key, loader);

            assertEquals(cachedValue, result, "应返回缓存值");
            assertEquals(0, loaderCallCount.get(), "Loader不应被调用");

            cacheService.delete(key);
        }

        @Test
        @DisplayName("应该正确处理空值缓存")
        void should_handleNullCache_correctly() {
            String key = TEST_KEY_PREFIX + "null-cache";
            Supplier<String> loader = () -> null;

            String result = cacheService.getCacheWithMutex(key, loader);

            assertNull(result, "应返回null");
            assertTrue(cacheService.isNullCache(key), "应设置空值缓存");

            cacheService.delete(key);
        }
    }

    @Nested
    @DisplayName("分布式锁测试")
    class DistributedLockTests {

        @Test
        @DisplayName("应该成功获取和释放锁")
        void should_acquireAndReleaseLock_successfully() {
            String lockKey = TEST_KEY_PREFIX + "lock:simple";

            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = lock.tryLock();

            assertTrue(acquired, "应成功获取锁");
            assertTrue(lock.isHeldByCurrentThread(), "当前线程应持有锁");

            lock.unlock();
            assertFalse(lock.isHeldByCurrentThread(), "释放后不应持有锁");
        }

        @Test
        @DisplayName("应该阻止重复获取锁")
        void should_preventDuplicateLock() throws InterruptedException {
            String lockKey = TEST_KEY_PREFIX + "lock:duplicate";
            RLock lock1 = redissonClient.getLock(lockKey);

            boolean acquired1 = lock1.tryLock();
            assertTrue(acquired1, "第一次获取锁应成功");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean acquired2 = new AtomicBoolean(true);
            Thread t = new Thread(() -> {
                RLock lock2 = redissonClient.getLock(lockKey);
                acquired2.set(lock2.tryLock());
                latch.countDown();
            });
            t.start();
            boolean allDone = latch.await(2, TimeUnit.SECONDS);
            if (!allDone) {
                System.out.println("线程未全部执行完，超时了！");
            }
            assertFalse(acquired2.get(), "其他线程获取锁应失败");

            if (lock1.isHeldByCurrentThread()) {
                lock1.unlock();
            }
        }

        @Test
        @DisplayName("应该正确处理并发锁竞争")
        void should_handleConcurrentLockContention() throws InterruptedException {
            String lockKey = TEST_KEY_PREFIX + "lock:concurrent";
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
                        RLock lock = redissonClient.getLock(lockKey);
                        if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                            try {
                                successCount.incrementAndGet();
                                Thread.sleep(50);
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
            boolean allDone = endLatch.await(5, TimeUnit.SECONDS);
            if (!allDone) {
                System.out.println("线程未全部执行完，超时了！");
            }
            executorService.shutdown();

            assertTrue(successCount.get() >= 1, "至少有一个线程应成功获取锁");
        }

        @Test
        @DisplayName("应该正确处理锁过期")
        void should_handleLockExpiry_correctly() throws InterruptedException {
            String lockKey = TEST_KEY_PREFIX + "lock:expiry";
            RLock lock = redissonClient.getLock(lockKey);

            boolean acquired = lock.tryLock(0, 1, TimeUnit.SECONDS);

            assertTrue(acquired, "应成功获取锁");

            Thread.sleep(1500);

            assertFalse(lock.isLocked(), "锁应已过期");
        }
    }

    @Nested
    @DisplayName("缓存穿透防护测试")
    class CachePenetrationTests {

        @Test
        @DisplayName("应该使用空值缓存防止缓存穿透")
        void should_preventCachePenetration_withNullCache() {
            String key = TEST_KEY_PREFIX + "penetration";
            Supplier<String> loader = () -> null;

            String result1 = cacheService.getCacheWithMutex(key, loader);

            assertNull(result1, "应返回null");
            assertTrue(cacheService.isNullCache(key), "应设置空值缓存");

            AtomicInteger loaderCallCount = new AtomicInteger(0);
            Supplier<String> loader2 = () -> {
                loaderCallCount.incrementAndGet();
                return null;
            };
            String result2 = cacheService.getCacheWithMutex(key, loader2);

            assertNull(result2, "应返回null");
            assertEquals(0, loaderCallCount.get(), "Loader不应被调用（应命中空值缓存）");

            cacheService.delete(key);
        }
    }

    @Nested
    @DisplayName("缓存雪崩防护测试")
    class CacheAvalancheTests {

        @Test
        @DisplayName("应该使用随机过期时间防止缓存雪崩")
        void should_preventCacheAvalanche_withRandomExpiry() {
            int keyCount = 10;
            long[] expireTimes = new long[keyCount];

            for (int i = 0; i < keyCount; i++) {
                String key = TEST_KEY_PREFIX + "avalanche:" + i;
                cacheService.setCache(key, "value" + i);

                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                expireTimes[i] = ttl != null ? ttl : 0;
            }

            boolean hasDifferentExpiry = false;
            for (int i = 1; i < keyCount; i++) {
                if (expireTimes[i] != expireTimes[0]) {
                    hasDifferentExpiry = true;
                    break;
                }
            }
            assertTrue(hasDifferentExpiry, "过期时间应该有差异");

            for (int i = 0; i < keyCount; i++) {
                cacheService.delete(TEST_KEY_PREFIX + "avalanche:" + i);
            }
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("应该高效处理批量缓存操作")
        void should_handleBatchCacheOperations_efficiently() {
            int operationCount = 100;
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < operationCount; i++) {
                String key = TEST_KEY_PREFIX + "perf:" + i;
                cacheService.setCache(key, "value" + i);
            }

            for (int i = 0; i < operationCount; i++) {
                String key = TEST_KEY_PREFIX + "perf:" + i;
                cacheService.getCache(key);
            }

            long endTime = System.currentTimeMillis();

            long duration = endTime - startTime;
            System.out.println("批量缓存操作耗时: " + duration + "ms");
            assertTrue(duration < 5000, "批量操作应在5秒内完成");

            for (int i = 0; i < operationCount; i++) {
                cacheService.delete(TEST_KEY_PREFIX + "perf:" + i);
            }
        }
    }
}
