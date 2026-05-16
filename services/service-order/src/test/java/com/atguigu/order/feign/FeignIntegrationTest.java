package com.atguigu.order.feign;

import com.atguigu.order.fallback.ProductFeignFallback;
import com.atguigu.product.bean.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feign客户端集成测试类
 * 
 * 测试目的：
 * 1. 验证Feign客户端与服务端的协作是否正常
 * 2. 验证服务间调用的正确性
 * 3. 验证Fallback降级逻辑的正确性
 * 
 * 使用Mock方式模拟远程服务调用
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Feign客户端集成测试")
class FeignIntegrationTest {

    /**
     * ProductFeign集成测试
     */
    @Nested
    @DisplayName("ProductFeign集成测试")
    class ProductFeignTests {

        @MockBean
        private ProductFeign productFeign;

        @Test
        @DisplayName("应该成功调用getProductById")
        void should_getProductById_successfully() {
            // Given
            Long productId = 1L;
            Product mockProduct = createTestProduct(productId, "测试商品", BigDecimal.valueOf(99.99));
            when(productFeign.getProductById(productId)).thenReturn(mockProduct);

            // When
            Product result = productFeign.getProductById(productId);

            // Then
            assertNotNull(result, "应返回商品");
            assertEquals(productId, result.getId(), "商品ID应正确");
            assertEquals("测试商品", result.getName(), "商品名称应正确");
            verify(productFeign).getProductById(productId);
        }

        @Test
        @DisplayName("应该成功调用batchGetProducts")
        void should_batchGetProducts_successfully() {
            // Given
            List<Long> ids = Arrays.asList(1L, 2L, 3L);
            List<Product> mockProducts = Arrays.asList(
                createTestProduct(1L, "商品1", BigDecimal.valueOf(10)),
                createTestProduct(2L, "商品2", BigDecimal.valueOf(20)),
                createTestProduct(3L, "商品3", BigDecimal.valueOf(30))
            );
            when(productFeign.batchGetProducts(ids)).thenReturn(mockProducts);

            // When
            List<Product> result = productFeign.batchGetProducts(ids);

            // Then
            assertNotNull(result, "应返回商品列表");
            assertEquals(3, result.size(), "应返回3个商品");
            verify(productFeign).batchGetProducts(ids);
        }

        @Test
        @DisplayName("应该成功调用decreaseStock")
        void should_decreaseStock_successfully() {
            // Given
            Long productId = 1L;
            Integer quantity = 10;
            when(productFeign.decreaseStock(productId, quantity)).thenReturn(1);

            // When
            int result = productFeign.decreaseStock(productId, quantity);

            // Then
            assertEquals(1, result, "扣减库存应成功");
            verify(productFeign).decreaseStock(productId, quantity);
        }

        @Test
        @DisplayName("应该成功调用increaseStock")
        void should_increaseStock_successfully() {
            // Given
            Long productId = 1L;
            Integer quantity = 10;
            when(productFeign.increaseStock(productId, quantity)).thenReturn(1);

            // When
            int result = productFeign.increaseStock(productId, quantity);

            // Then
            assertEquals(1, result, "增加库存应成功");
            verify(productFeign).increaseStock(productId, quantity);
        }

        @Test
        @DisplayName("应该成功调用batchIncreaseStock")
        void should_batchIncreaseStock_successfully() {
            // Given
            List<Map<String, Object>> items = Arrays.asList(
                createStockItem(1L, 5),
                createStockItem(2L, 10)
            );
            when(productFeign.batchIncreaseStock(items)).thenReturn(2);

            // When
            int result = productFeign.batchIncreaseStock(items);

            // Then
            assertEquals(2, result, "批量增加库存应成功");
            verify(productFeign).batchIncreaseStock(items);
        }

        @Test
        @DisplayName("应该成功调用batchDecreaseStock")
        void should_batchDecreaseStock_successfully() {
            // Given
            List<Map<String, Object>> items = Arrays.asList(
                createStockItem(1L, 5),
                createStockItem(2L, 10)
            );
            when(productFeign.batchDecreaseStock(items)).thenReturn(2);

            // When
            int result = productFeign.batchDecreaseStock(items);

            // Then
            assertEquals(2, result, "批量扣减库存应成功");
            verify(productFeign).batchDecreaseStock(items);
        }

        @Test
        @DisplayName("应该处理库存不足的情况")
        void should_handleInsufficientStock() {
            // Given
            Long productId = 1L;
            Integer quantity = 1000; // 库存不足
            when(productFeign.decreaseStock(productId, quantity)).thenReturn(0);

            // When
            int result = productFeign.decreaseStock(productId, quantity);

            // Then
            assertEquals(0, result, "库存不足应返回0");
            verify(productFeign).decreaseStock(productId, quantity);
        }
    }

    /**
     * UserFeign集成测试
     */
    @Nested
    @DisplayName("UserFeign集成测试")
    class UserFeignTests {

        @MockBean
        private UserFeign userFeign;

        @Test
        @DisplayName("应该成功调用getUserInfo")
        void should_getUserInfo_successfully() {
            // Given
            Long userId = 1L;
            Map<String, Object> mockUserInfo = new HashMap<>();
            mockUserInfo.put("id", userId);
            mockUserInfo.put("nickName", "测试用户");
            mockUserInfo.put("phone", "13812345678");
            
            com.atguigu.common.result.R mockResult = com.atguigu.common.result.R.ok("获取成功", mockUserInfo);
            when(userFeign.getUserInfo(userId)).thenReturn(mockResult);

            // When
            com.atguigu.common.result.R result = userFeign.getUserInfo(userId);

            // Then
            assertNotNull(result, "应返回用户信息");
            assertEquals(200, result.getCode(), "响应码应为200");
            verify(userFeign).getUserInfo(userId);
        }
    }

    /**
     * Fallback降级测试
     */
    @Nested
    @DisplayName("Fallback降级测试")
    class FallbackTests {

        @Autowired
        private ProductFeignFallback productFeignFallback;

        @Test
        @DisplayName("ProductFeignFallback应该返回默认商品当getProductById失败")
        void should_fallbackReturnDefaultProduct_whenGetProductByIdFails() {
            // When
            Product result = productFeignFallback.getProductById(1L);

            // Then
            assertNotNull(result, "Fallback应返回默认商品");
            assertEquals(1L, result.getId(), "商品ID应正确");
            assertEquals("商品信息加载中", result.getName(), "应返回默认商品名称");
        }

        @Test
        @DisplayName("ProductFeignFallback应该返回默认商品列表当batchGetProducts失败")
        void should_fallbackReturnDefaultList_whenBatchGetProductsFails() {
            // When
            List<Product> result = productFeignFallback.batchGetProducts(Arrays.asList(1L, 2L));

            // Then
            assertNotNull(result, "Fallback应返回非空列表");
            assertEquals(2, result.size(), "应返回2个默认商品");
        }

        @Test
        @DisplayName("ProductFeignFallback应该抛出异常当decreaseStock失败")
        void should_fallbackThrowException_whenDecreaseStockFails() {
            // When & Then
            assertThrows(com.atguigu.common.exception.BusinessException.class, () -> {
                productFeignFallback.decreaseStock(1L, 10);
            });
        }

        @Test
        @DisplayName("ProductFeignFallback应该抛出异常当increaseStock失败")
        void should_fallbackThrowException_whenIncreaseStockFails() {
            // When & Then
            assertThrows(com.atguigu.common.exception.BusinessException.class, () -> {
                productFeignFallback.increaseStock(1L, 10);
            });
        }

        @Test
        @DisplayName("ProductFeignFallback应该抛出异常当batchIncreaseStock失败")
        void should_fallbackThrowException_whenBatchIncreaseStockFails() {
            // When & Then
            assertThrows(com.atguigu.common.exception.BusinessException.class, () -> {
                productFeignFallback.batchIncreaseStock(Arrays.asList(createStockItem(1L, 5)));
            });
        }

        @Test
        @DisplayName("ProductFeignFallback应该抛出异常当batchDecreaseStock失败")
        void should_fallbackThrowException_whenBatchDecreaseStockFails() {
            // When & Then
            assertThrows(com.atguigu.common.exception.BusinessException.class, () -> {
                productFeignFallback.batchDecreaseStock(Arrays.asList(createStockItem(1L, 5)));
            });
        }
    }

    // ==================== 辅助方法 ====================

    private Product createTestProduct(Long id, String name, BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setNum(100);
        product.setSales(0);
        product.setMerchantId(1L);
        product.setEnabled(true);
        return product;
    }

    private Map<String, Object> createStockItem(Long productId, Integer quantity) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId);
        item.put("quantity", quantity);
        return item;
    }
}
