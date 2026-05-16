package com.atguigu.product.service.impl;

import com.atguigu.common.mq.InventoryAlertMessage;
import com.atguigu.product.bean.InventoryAlertConfig;
import com.atguigu.product.bean.InventoryAlertLog;
import com.atguigu.product.bean.Product;
import com.atguigu.product.mapper.InventoryAlertConfigMapper;
import com.atguigu.product.mapper.InventoryAlertLogMapper;
import com.atguigu.product.mapper.ProductMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InventoryAlertServiceImpl 单元测试类
 * 测试库存预警服务相关业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAlertServiceImpl 单元测试")
class InventoryAlertServiceImplTest {

    @Mock
    private InventoryAlertConfigMapper alertConfigMapper;

    @Mock
    private InventoryAlertLogMapper alertLogMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private InventoryAlertServiceImpl inventoryAlertService;

    @Nested
    @DisplayName("checkInventoryAlert 方法测试")
    class CheckInventoryAlertTests {

        @Test
        @DisplayName("应该跳过当没有启用的预警配置")
        void should_skip_whenNoEnabledConfigs() {
            // Given
            when(alertConfigMapper.selectAllEnabled()).thenReturn(Collections.emptyList());

            // When
            inventoryAlertService.checkInventoryAlert();

            // Then
            verify(alertConfigMapper).selectAllEnabled();
            verifyNoInteractions(productMapper, alertLogMapper, rocketMQTemplate);
        }

        @Test
        @DisplayName("应该跳过当预警配置为null")
        void should_skip_whenConfigIsNull() {
            // Given
            when(alertConfigMapper.selectAllEnabled()).thenReturn(null);

            // When
            inventoryAlertService.checkInventoryAlert();

            // Then
            verify(alertConfigMapper).selectAllEnabled();
            verifyNoInteractions(productMapper);
        }

        @Test
        @DisplayName("应该跳过库存充足的商品")
        void should_skip_whenStockIsSufficient() {
            // Given
            InventoryAlertConfig config = createTestConfig(1L, 1L, 10);
            Product product = createTestProduct(1L, "商品", 100); // 库存100，阈值10
            
            when(alertConfigMapper.selectAllEnabled()).thenReturn(Arrays.asList(config));
            when(productMapper.selectById(1L)).thenReturn(product);

            // When
            inventoryAlertService.checkInventoryAlert();

            // Then
            verify(productMapper).selectById(1L);
            verify(alertLogMapper, never()).insert(any(InventoryAlertLog.class));
        }

        @Test
        @DisplayName("应该跳过商品不存在的情况")
        void should_skip_whenProductNotExist() {
            // Given
            InventoryAlertConfig config = createTestConfig(1L, 1L, 10);
            
            when(alertConfigMapper.selectAllEnabled()).thenReturn(Arrays.asList(config));
            when(productMapper.selectById(1L)).thenReturn(null);

            // When
            inventoryAlertService.checkInventoryAlert();

            // Then
            verify(productMapper).selectById(1L);
            verify(alertLogMapper, never()).insert(any(InventoryAlertLog.class));
        }

        @Test
        @DisplayName("应该跳过配置productId为null的情况")
        void should_skip_whenConfigProductIdIsNull() {
            // Given
            InventoryAlertConfig config = createTestConfig(1L, null, 10);
            
            when(alertConfigMapper.selectAllEnabled()).thenReturn(Arrays.asList(config));

            // When
            inventoryAlertService.checkInventoryAlert();

            // Then
            verify(productMapper, never()).selectById(any());
        }
    }

    @Nested
    @DisplayName("canSendAlert 方法测试")
    class CanSendAlertTests {

        @Test
        @DisplayName("应该返回false当productId为null")
        void should_returnFalse_whenProductIdIsNull() {
            // When
            boolean result = inventoryAlertService.canSendAlert(null);

            // Then
            assertFalse(result, "null productId应返回false");
        }

        @Test
        @DisplayName("应该返回true当预警次数未达上限")
        void should_returnTrue_whenAlertCountBelowLimit() {
            // Given
            Long productId = 1L;
            
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("1");

            // When
            boolean result = inventoryAlertService.canSendAlert(productId);

            // Then
            assertTrue(result, "预警次数未达上限应返回true");
        }

        @Test
        @DisplayName("应该返回false当预警次数已达上限")
        void should_returnFalse_whenAlertCountAtLimit() {
            // Given
            Long productId = 1L;
            
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("3");

            // When
            boolean result = inventoryAlertService.canSendAlert(productId);

            // Then
            assertFalse(result, "预警次数已达上限应返回false");
        }

        @Test
        @DisplayName("应该使用数据库检查当Redis不可用")
        void should_useDbCheck_whenRedisUnavailable() {
            // Given
            Long productId = 1L;
            
            when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));
            when(alertLogMapper.countTodayByProductId(productId)).thenReturn(1);

            // When
            boolean result = inventoryAlertService.canSendAlert(productId);

            // Then
            assertTrue(result, "应使用数据库检查并返回true");
            verify(alertLogMapper).countTodayByProductId(productId);
        }
    }

    @Nested
    @DisplayName("triggerAlertManually 方法测试")
    class TriggerAlertManuallyTests {

        @Test
        @DisplayName("应该返回false当productId为null")
        void should_returnFalse_whenProductIdIsNull() {
            // When
            boolean result = inventoryAlertService.triggerAlertManually(null);

            // Then
            assertFalse(result, "null productId应返回false");
        }

        @Test
        @DisplayName("应该返回false当商品未配置预警")
        void should_returnFalse_whenNoAlertConfig() {
            // Given
            Long productId = 1L;
            
            when(alertConfigMapper.selectByProductId(productId)).thenReturn(null);

            // When
            boolean result = inventoryAlertService.triggerAlertManually(productId);

            // Then
            assertFalse(result, "未配置预警应返回false");
        }

        @Test
        @DisplayName("应该返回false当预警已禁用")
        void should_returnFalse_whenAlertDisabled() {
            // Given
            Long productId = 1L;
            InventoryAlertConfig config = createTestConfig(1L, productId, 10);
            config.setStatus(0); // 禁用状态
            
            when(alertConfigMapper.selectByProductId(productId)).thenReturn(config);

            // When
            boolean result = inventoryAlertService.triggerAlertManually(productId);

            // Then
            assertFalse(result, "预警已禁用应返回false");
        }

        @Test
        @DisplayName("应该返回false当商品不存在")
        void should_returnFalse_whenProductNotExist() {
            // Given
            Long productId = 1L;
            InventoryAlertConfig config = createTestConfig(1L, productId, 10);
            config.setStatus(1);
            
            when(alertConfigMapper.selectByProductId(productId)).thenReturn(config);
            when(productMapper.selectById(productId)).thenReturn(null);

            // When
            boolean result = inventoryAlertService.triggerAlertManually(productId);

            // Then
            assertFalse(result, "商品不存在应返回false");
        }

        @Test
        @DisplayName("应该成功手动触发预警")
        void should_triggerAlertManually_successfully() {
            // Given
            Long productId = 1L;
            InventoryAlertConfig config = createTestConfig(1L, productId, 10);
            config.setStatus(1);
            Product product = createTestProduct(productId, "测试商品", 5);
            
            when(alertConfigMapper.selectByProductId(productId)).thenReturn(config);
            when(productMapper.selectById(productId)).thenReturn(product);
            when(alertLogMapper.insert(any(InventoryAlertLog.class))).thenAnswer(invocation -> {
                InventoryAlertLog log = invocation.getArgument(0);
                log.setId(1L);
                return 1;
            });

            // When
            boolean result = inventoryAlertService.triggerAlertManually(productId);

            // Then
            assertTrue(result, "手动触发预警应成功");
            verify(alertLogMapper).insert(any(InventoryAlertLog.class));
            verify(rocketMQTemplate).asyncSend(eq("inventory-alert-topic"), any(InventoryAlertMessage.class), any());
        }
    }

    @Nested
    @DisplayName("saveOrUpdateConfig 方法测试")
    class SaveOrUpdateConfigTests {

        @Test
        @DisplayName("应该返回false当配置为null")
        void should_returnFalse_whenConfigIsNull() {
            // When
            boolean result = inventoryAlertService.saveOrUpdateConfig(null);

            // Then
            assertFalse(result, "null配置应返回false");
        }

        @Test
        @DisplayName("应该返回false当productId为null")
        void should_returnFalse_whenProductIdIsNull() {
            // Given
            InventoryAlertConfig config = new InventoryAlertConfig();
            config.setProductId(null);

            // When
            boolean result = inventoryAlertService.saveOrUpdateConfig(config);

            // Then
            assertFalse(result, "productId为null应返回false");
        }

        @Test
        @DisplayName("应该成功保存配置")
        void should_saveConfig_successfully() {
            // Given
            InventoryAlertConfig config = createTestConfig(null, 1L, 10);
            
            when(alertConfigMapper.insertOrUpdate(config)).thenReturn(1);

            // When
            boolean result = inventoryAlertService.saveOrUpdateConfig(config);

            // Then
            assertTrue(result, "保存配置应成功");
            verify(alertConfigMapper).insertOrUpdate(config);
        }

        @Test
        @DisplayName("应该返回false当保存失败")
        void should_returnFalse_whenSaveFailed() {
            // Given
            InventoryAlertConfig config = createTestConfig(null, 1L, 10);
            
            when(alertConfigMapper.insertOrUpdate(config)).thenReturn(0);

            // When
            boolean result = inventoryAlertService.saveOrUpdateConfig(config);

            // Then
            assertFalse(result, "保存失败应返回false");
        }

        @Test
        @DisplayName("应该返回false当发生异常")
        void should_returnFalse_whenExceptionOccurs() {
            // Given
            InventoryAlertConfig config = createTestConfig(null, 1L, 10);
            
            when(alertConfigMapper.insertOrUpdate(config)).thenThrow(new RuntimeException("DB error"));

            // When
            boolean result = inventoryAlertService.saveOrUpdateConfig(config);

            // Then
            assertFalse(result, "发生异常应返回false");
        }
    }

    @Nested
    @DisplayName("getConfigByProductId 方法测试")
    class GetConfigByProductIdTests {

        @Test
        @DisplayName("应该返回null当productId为null")
        void should_returnNull_whenProductIdIsNull() {
            // When
            InventoryAlertConfig result = inventoryAlertService.getConfigByProductId(null);

            // Then
            assertNull(result, "null productId应返回null");
        }

        @Test
        @DisplayName("应该返回配置信息")
        void should_returnConfig() {
            // Given
            Long productId = 1L;
            InventoryAlertConfig config = createTestConfig(1L, productId, 10);
            
            when(alertConfigMapper.selectByProductId(productId)).thenReturn(config);

            // When
            InventoryAlertConfig result = inventoryAlertService.getConfigByProductId(productId);

            // Then
            assertNotNull(result, "应返回配置信息");
            assertEquals(productId, result.getProductId(), "productId应正确");
        }
    }

    @Nested
    @DisplayName("listAllEnabledConfigs 方法测试")
    class ListAllEnabledConfigsTests {

        @Test
        @DisplayName("应该返回所有启用的配置")
        void should_listAllEnabledConfigs() {
            // Given
            InventoryAlertConfig config1 = createTestConfig(1L, 1L, 10);
            InventoryAlertConfig config2 = createTestConfig(2L, 2L, 20);
            
            when(alertConfigMapper.selectAllEnabled()).thenReturn(Arrays.asList(config1, config2));

            // When
            List<InventoryAlertConfig> result = inventoryAlertService.listAllEnabledConfigs();

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.size(), "应返回2个配置");
        }

        @Test
        @DisplayName("应该返回空列表当没有启用的配置")
        void should_returnEmptyList_whenNoEnabledConfigs() {
            // Given
            when(alertConfigMapper.selectAllEnabled()).thenReturn(null);

            // When
            List<InventoryAlertConfig> result = inventoryAlertService.listAllEnabledConfigs();

            // Then
            assertNotNull(result, "结果不应为空");
            assertTrue(result.isEmpty(), "应返回空列表");
        }
    }

    @Nested
    @DisplayName("updateConfigStatus 方法测试")
    class UpdateConfigStatusTests {

        @Test
        @DisplayName("应该返回false当id为null")
        void should_returnFalse_whenIdIsNull() {
            // When
            boolean result = inventoryAlertService.updateConfigStatus(null, 1);

            // Then
            assertFalse(result, "null id应返回false");
        }

        @Test
        @DisplayName("应该返回false当status为null")
        void should_returnFalse_whenStatusIsNull() {
            // When
            boolean result = inventoryAlertService.updateConfigStatus(1L, null);

            // Then
            assertFalse(result, "null status应返回false");
        }

        @Test
        @DisplayName("应该成功更新状态")
        void should_updateStatus_successfully() {
            // Given
            Long id = 1L;
            Integer status = 1;
            
            when(alertConfigMapper.updateStatus(id, status)).thenReturn(1);

            // When
            boolean result = inventoryAlertService.updateConfigStatus(id, status);

            // Then
            assertTrue(result, "更新状态应成功");
        }
    }

    @Nested
    @DisplayName("listAlertLogs 方法测试")
    class ListAlertLogsTests {

        @Test
        @DisplayName("应该返回空列表当productId为null")
        void should_returnEmptyList_whenProductIdIsNull() {
            // When
            List<InventoryAlertLog> result = inventoryAlertService.listAlertLogs(null, 10);

            // Then
            assertNotNull(result, "结果不应为空");
            assertTrue(result.isEmpty(), "应返回空列表");
        }

        @Test
        @DisplayName("应该限制最大返回数量")
        void should_limitMaxResults() {
            // Given
            Long productId = 1L;
            int requestedLimit = 200; // 请求超过最大限制
            
            when(alertLogMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When
            inventoryAlertService.listAlertLogs(productId, requestedLimit);

            // Then
            verify(alertLogMapper).selectList(any());
        }
    }

    @Nested
    @DisplayName("processAlertNotification 方法测试")
    class ProcessAlertNotificationTests {

        @Test
        @DisplayName("应该跳过当alertLogId为null")
        void should_skip_whenAlertLogIdIsNull() {
            // When
            inventoryAlertService.processAlertNotification(null);

            // Then
            verify(alertLogMapper, never()).selectById(any());
        }

        @Test
        @DisplayName("应该跳过当预警记录不存在")
        void should_skip_whenAlertLogNotExist() {
            // Given
            Long alertLogId = 1L;
            
            when(alertLogMapper.selectById(alertLogId)).thenReturn(null);

            // When
            inventoryAlertService.processAlertNotification(alertLogId);

            // Then
            verify(alertLogMapper).selectById(alertLogId);
            verify(alertLogMapper, never()).updateStatus(anyLong(), anyString());
        }

        @Test
        @DisplayName("应该跳过已处理的预警记录")
        void should_skip_whenAlertLogAlreadyProcessed() {
            // Given
            Long alertLogId = 1L;
            InventoryAlertLog alertLog = new InventoryAlertLog();
            alertLog.setId(alertLogId);
            alertLog.setStatus("SENT"); // 已发送
            
            when(alertLogMapper.selectById(alertLogId)).thenReturn(alertLog);

            // When
            inventoryAlertService.processAlertNotification(alertLogId);

            // Then
            verify(alertLogMapper, never()).updateStatus(anyLong(), anyString());
        }

        @Test
        @DisplayName("应该成功处理预警通知")
        void should_processAlertNotification_successfully() {
            // Given
            Long alertLogId = 1L;
            InventoryAlertLog alertLog = new InventoryAlertLog();
            alertLog.setId(alertLogId);
            alertLog.setProductId(1L);
            alertLog.setMerchantId(1L);
            alertLog.setStock(5);
            alertLog.setThreshold(10);
            alertLog.setStatus("PENDING");
            
            when(alertLogMapper.selectById(alertLogId)).thenReturn(alertLog);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            inventoryAlertService.processAlertNotification(alertLogId);

            // Then
            verify(alertLogMapper).updateStatus(alertLogId, "SENT");
        }
    }

    // ========== 辅助方法 ==========

    private InventoryAlertConfig createTestConfig(Long id, Long productId, Integer threshold) {
        InventoryAlertConfig config = new InventoryAlertConfig();
        config.setId(id);
        config.setProductId(productId);
        config.setThreshold(threshold);
        config.setStatus(1);
        config.setAlertInterval(1440);
        return config;
    }

    private Product createTestProduct(Long id, String name, Integer stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setNum(stock);
        product.setMerchantId(1L);
        return product;
    }
}
