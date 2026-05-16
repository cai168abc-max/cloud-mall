package com.atguigu.product.service.impl;

import com.atguigu.common.cache.BloomFilterService;
import com.atguigu.common.cache.CacheService;
import com.atguigu.product.bean.Product;
import com.atguigu.product.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProductServiceImpl 单元测试类
 * 测试商品服务相关业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl 单元测试")
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CacheService cacheService;

    @Mock
    private BloomFilterService bloomFilterService;

    @InjectMocks
    private ProductServiceImpl productService;

    private static final String PRODUCT_CACHE_KEY_PREFIX = "product:";

    @Nested
    @DisplayName("getProductById 方法测试")
    class GetProductByIdTests {

        @Test
        @DisplayName("应该返回null当productId为null")
        void should_returnNull_whenProductIdIsNull() {
            // When
            Product result = productService.getProductById(null);

            // Then
            assertNull(result, "null productId应返回null");
        }

        @Test
        @DisplayName("应该通过布隆过滤器快速判断商品不存在")
        void should_returnNull_whenBloomFilterSaysNotExist() {
            // Given
            Long productId = 999L;
            when(bloomFilterService.mightContainProduct(productId)).thenReturn(false);

            // When
            Product result = productService.getProductById(productId);

            // Then
            assertNull(result, "布隆过滤器判断不存在时应返回null");
            verify(bloomFilterService).mightContainProduct(productId);
            verifyNoInteractions(cacheService, productMapper);
        }

        @Test
        @DisplayName("应该返回null当命中空值缓存")
        void should_returnNull_whenNullCacheHit() {
            // Given
            Long productId = 999L;
            String cacheKey = PRODUCT_CACHE_KEY_PREFIX + productId;
            
            when(bloomFilterService.mightContainProduct(productId)).thenReturn(true);
            when(cacheService.isNullCache(cacheKey)).thenReturn(true);

            // When
            Product result = productService.getProductById(productId);

            // Then
            assertNull(result, "命中空值缓存时应返回null");
        }

        @Test
        @DisplayName("应该从缓存返回商品")
        void should_returnProductFromCache() {
            // Given
            Long productId = 1L;
            String cacheKey = PRODUCT_CACHE_KEY_PREFIX + productId;
            
            Product cachedProduct = createTestProduct(productId, "测试商品", BigDecimal.valueOf(100));
            
            when(bloomFilterService.mightContainProduct(productId)).thenReturn(true);
            when(cacheService.isNullCache(cacheKey)).thenReturn(false);
            when(cacheService.getCache(cacheKey)).thenReturn(cachedProduct);

            // When
            Product result = productService.getProductById(productId);

            // Then
            assertNotNull(result, "应返回商品");
            assertEquals(productId, result.getId(), "商品ID应正确");
            verify(cacheService, never()).setCache(anyString(), any());
        }

        @Test
        @DisplayName("应该从数据库查询并缓存商品")
        void should_queryFromDbAndCache() {
            // Given
            Long productId = 1L;
            String cacheKey = PRODUCT_CACHE_KEY_PREFIX + productId;
            
            Product dbProduct = createTestProduct(productId, "测试商品", BigDecimal.valueOf(100));
            
            when(bloomFilterService.mightContainProduct(productId)).thenReturn(true);
            when(cacheService.isNullCache(cacheKey)).thenReturn(false);
            when(cacheService.getCache(cacheKey)).thenReturn(null);
            when(productMapper.selectById(productId)).thenReturn(dbProduct);

            // When
            Product result = productService.getProductById(productId);

            // Then
            assertNotNull(result, "应返回商品");
            assertEquals(productId, result.getId(), "商品ID应正确");
            verify(cacheService).setCache(cacheKey, dbProduct);
        }

        @Test
        @DisplayName("应该设置空值缓存当商品不存在")
        void should_setNullCache_whenProductNotExist() {
            // Given
            Long productId = 999L;
            String cacheKey = PRODUCT_CACHE_KEY_PREFIX + productId;
            
            when(bloomFilterService.mightContainProduct(productId)).thenReturn(true);
            when(cacheService.isNullCache(cacheKey)).thenReturn(false);
            when(cacheService.getCache(cacheKey)).thenReturn(null);
            when(productMapper.selectById(productId)).thenReturn(null);

            // When
            Product result = productService.getProductById(productId);

            // Then
            assertNull(result, "商品不存在应返回null");
            verify(cacheService).setNullCache(cacheKey);
        }
    }

    @Nested
    @DisplayName("batchGetProducts 方法测试")
    class BatchGetProductsTests {

        @Test
        @DisplayName("应该返回空列表当ids为null")
        void should_returnEmptyList_whenIdsIsNull() {
            // When
            List<Product> result = productService.batchGetProducts(null);

            // Then
            assertNotNull(result, "结果不应为null");
            assertTrue(result.isEmpty(), "结果应为空列表");
        }

        @Test
        @DisplayName("应该返回空列表当ids为空")
        void should_returnEmptyList_whenIdsIsEmpty() {
            // When
            List<Product> result = productService.batchGetProducts(Collections.emptyList());

            // Then
            assertNotNull(result, "结果不应为null");
            assertTrue(result.isEmpty(), "结果应为空列表");
        }

        @Test
        @DisplayName("应该批量获取商品")
        void should_batchGetProducts() {
            // Given
            List<Long> ids = Arrays.asList(1L, 2L);
            Product product1 = createTestProduct(1L, "商品1", BigDecimal.valueOf(100));
            Product product2 = createTestProduct(2L, "商品2", BigDecimal.valueOf(200));
            
            when(cacheService.isNullCache(anyString())).thenReturn(false);
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(productMapper.selectBatchIds(ids)).thenReturn(Arrays.asList(product1, product2));

            // When
            List<Product> result = productService.batchGetProducts(ids);

            // Then
            assertNotNull(result, "结果不应为null");
            assertEquals(2, result.size(), "应返回2个商品");
        }
    }

    @Nested
    @DisplayName("saveOrUpdate 方法测试")
    class SaveOrUpdateTests {

        @Test
        @DisplayName("应该成功创建新商品")
        void should_createNewProduct() {
            // Given
            Product product = createTestProduct(null, "新商品", BigDecimal.valueOf(100));
            
            when(productMapper.insertProduct(product)).thenAnswer(invocation -> {
                Product p = invocation.getArgument(0);
                p.setId(1L);
                return 1;
            });

            // When
            Product result = productService.saveOrUpdate(product);

            // Then
            assertNotNull(result, "结果不应为空");
            assertNotNull(result.getId(), "ID应被设置");
            verify(productMapper).insertProduct(product);
        }

        @Test
        @DisplayName("应该成功更新商品并删除缓存")
        void should_updateProductAndDeleteCache() {
            // Given
            Product product = createTestProduct(1L, "更新商品", BigDecimal.valueOf(150));
            String cacheKey = PRODUCT_CACHE_KEY_PREFIX + product.getId();
            
            when(productMapper.updateProduct(product)).thenReturn(1);

            // When
            Product result = productService.saveOrUpdate(product);

            // Then
            assertNotNull(result, "结果不应为空");
            verify(cacheService).deleteWithDoubleRemoval(cacheKey);
            verify(productMapper).updateProduct(product);
        }
    }

    @Nested
    @DisplayName("deleteProduct 方法测试")
    class DeleteProductTests {

        @Test
        @DisplayName("应该成功删除商品")
        void should_deleteProduct_successfully() {
            // Given
            Long productId = 1L;
            when(productMapper.deleteById(productId)).thenReturn(1);

            // When
            boolean result = productService.deleteProduct(productId);

            // Then
            assertTrue(result, "删除应成功");
            verify(productMapper).deleteById(productId);
        }

        @Test
        @DisplayName("应该返回false当商品不存在")
        void should_returnFalse_whenProductNotExist() {
            // Given
            Long productId = 999L;
            when(productMapper.deleteById(productId)).thenReturn(0);

            // When
            boolean result = productService.deleteProduct(productId);

            // Then
            assertFalse(result, "删除不存在的商品应返回false");
        }
    }

    @Nested
    @DisplayName("decreaseStock 方法测试")
    class DecreaseStockTests {

        @Test
        @DisplayName("应该成功扣减库存")
        void should_decreaseStock_successfully() {
            // Given
            Long productId = 1L;
            Integer quantity = 5;
            
            when(productMapper.decreaseStock(productId, quantity)).thenReturn(1);

            // When
            boolean result = productService.decreaseStock(productId, quantity);

            // Then
            assertTrue(result, "扣减库存应成功");
            verify(productMapper).decreaseStock(productId, quantity);
        }

        @Test
        @DisplayName("应该返回false当库存不足")
        void should_returnFalse_whenStockInsufficient() {
            // Given
            Long productId = 1L;
            Integer quantity = 100;
            
            when(productMapper.decreaseStock(productId, quantity)).thenReturn(0);

            // When
            boolean result = productService.decreaseStock(productId, quantity);

            // Then
            assertFalse(result, "库存不足应返回false");
        }
    }

    @Nested
    @DisplayName("increaseStock 方法测试")
    class IncreaseStockTests {

        @Test
        @DisplayName("应该成功增加库存")
        void should_increaseStock_successfully() {
            // Given
            Long productId = 1L;
            Integer quantity = 10;
            
            when(productMapper.increaseStock(productId, quantity)).thenReturn(1);

            // When
            boolean result = productService.increaseStock(productId, quantity);

            // Then
            assertTrue(result, "增加库存应成功");
            verify(productMapper).increaseStock(productId, quantity);
        }
    }

    @Nested
    @DisplayName("updatePrice 方法测试")
    class UpdatePriceTests {

        @Test
        @DisplayName("应该成功更新价格")
        void should_updatePrice_successfully() {
            // Given
            Long productId = 1L;
            BigDecimal newPrice = BigDecimal.valueOf(199.99);
            
            when(productMapper.updatePrice(productId, newPrice)).thenReturn(1);

            // When
            boolean result = productService.updatePrice(productId, newPrice);

            // Then
            assertTrue(result, "更新价格应成功");
            verify(productMapper).updatePrice(productId, newPrice);
        }
    }

    @Nested
    @DisplayName("updateEnabled 方法测试")
    class UpdateEnabledTests {

        @Test
        @DisplayName("应该成功启用商品")
        void should_enableProduct_successfully() {
            // Given
            Long productId = 1L;
            Boolean enabled = true;
            
            when(productMapper.updateEnabled(productId, enabled)).thenReturn(1);

            // When
            boolean result = productService.updateEnabled(productId, enabled);

            // Then
            assertTrue(result, "启用商品应成功");
        }

        @Test
        @DisplayName("应该成功禁用商品")
        void should_disableProduct_successfully() {
            // Given
            Long productId = 1L;
            Boolean enabled = false;
            
            when(productMapper.updateEnabled(productId, enabled)).thenReturn(1);

            // When
            boolean result = productService.updateEnabled(productId, enabled);

            // Then
            assertTrue(result, "禁用商品应成功");
        }
    }

    @Nested
    @DisplayName("searchProducts 方法测试")
    class SearchProductsTests {

        @Test
        @DisplayName("应该搜索商品并按价格升序排序")
        void should_searchProductsAndSortByPriceAsc() {
            // Given
            String keyword = "测试";
            String sortBy = "price";
            boolean ascending = true;
            
            Product product1 = createTestProduct(1L, "测试商品A", BigDecimal.valueOf(100));
            Product product2 = createTestProduct(2L, "测试商品B", BigDecimal.valueOf(50));
            
            Page<Product> mockPage = new Page<>(1, 100);
            mockPage.setRecords(Arrays.asList(product1, product2));
            
            when(productMapper.searchProducts(any(Page.class), eq(keyword), isNull())).thenReturn(mockPage);

            // When
            List<Product> result = productService.searchProducts(keyword, null, sortBy, ascending);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.size(), "应返回2个商品");
            // 验证排序：价格低的在前
            assertEquals(BigDecimal.valueOf(50), result.get(0).getPrice(), "第一个商品价格应为50");
            assertEquals(BigDecimal.valueOf(100), result.get(1).getPrice(), "第二个商品价格应为100");
        }

        @Test
        @DisplayName("应该搜索商品并按销量降序排序")
        void should_searchProductsAndSortBySalesDesc() {
            // Given
            String keyword = "测试";
            String sortBy = "sales";
            boolean ascending = false;
            
            Product product1 = createTestProduct(1L, "测试商品A", BigDecimal.valueOf(100));
            product1.setSales(100);
            Product product2 = createTestProduct(2L, "测试商品B", BigDecimal.valueOf(50));
            product2.setSales(200);
            
            Page<Product> mockPage = new Page<>(1, 100);
            mockPage.setRecords(Arrays.asList(product1, product2));
            
            when(productMapper.searchProducts(any(Page.class), eq(keyword), isNull())).thenReturn(mockPage);

            // When
            List<Product> result = productService.searchProducts(keyword, null, sortBy, ascending);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.size(), "应返回2个商品");
            // 验证排序：销量高的在前
            assertEquals(200, result.get(0).getSales(), "第一个商品销量应为200");
            assertEquals(100, result.get(1).getSales(), "第二个商品销量应为100");
        }

        @Test
        @DisplayName("应该返回空列表当分类ID格式错误")
        void should_returnEmptyList_whenCategoryIdInvalid() {
            // Given
            String invalidCategory = "invalid";

            // When
            List<Product> result = productService.searchProducts("测试", invalidCategory, null, true);

            // Then
            assertNotNull(result, "结果不应为空");
            assertTrue(result.isEmpty(), "应返回空列表");
        }
    }

    @Nested
    @DisplayName("listProductsByPage 方法测试")
    class ListProductsByPageTests {

        @Test
        @DisplayName("应该分页查询商品")
        void should_listProductsByPage() {
            // Given
            int pageNum = 1;
            int pageSize = 10;
            
            Product product1 = createTestProduct(1L, "商品1", BigDecimal.valueOf(100));
            Product product2 = createTestProduct(2L, "商品2", BigDecimal.valueOf(200));
            
            Page<Product> mockPage = new Page<>(pageNum, pageSize);
            mockPage.setRecords(Arrays.asList(product1, product2));
            mockPage.setTotal(2);
            
            when(productMapper.selectPageAll(any(Page.class))).thenReturn(mockPage);

            // When
            IPage<Product> result = productService.listProductsByPage(pageNum, pageSize);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.getRecords().size(), "应返回2个商品");
            assertEquals(2, result.getTotal(), "总数应为2");
        }
    }

    @Nested
    @DisplayName("listHotProducts 方法测试")
    class ListHotProductsTests {

        @Test
        @DisplayName("应该返回热门商品")
        void should_listHotProducts() {
            // Given
            int limit = 5;
            
            Product product1 = createTestProduct(1L, "热门商品1", BigDecimal.valueOf(100));
            product1.setSales(1000);
            Product product2 = createTestProduct(2L, "热门商品2", BigDecimal.valueOf(200));
            product2.setSales(800);
            
            when(productMapper.selectHotProducts(limit)).thenReturn(Arrays.asList(product1, product2));

            // When
            List<Product> result = productService.listHotProducts(limit);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.size(), "应返回2个热门商品");
        }
    }

    @Nested
    @DisplayName("listByCategory 方法测试")
    class ListByCategoryTests {

        @Test
        @DisplayName("应该返回分类下的商品")
        void should_listByCategory() {
            // Given
            Long categoryId = 1L;
            
            Product product1 = createTestProduct(1L, "分类商品1", BigDecimal.valueOf(100));
            product1.setCategoryId(categoryId);
            Product product2 = createTestProduct(2L, "分类商品2", BigDecimal.valueOf(200));
            product2.setCategoryId(categoryId);
            
            when(productMapper.selectByCategoryId(categoryId)).thenReturn(Arrays.asList(product1, product2));

            // When
            List<Product> result = productService.listByCategory(categoryId);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.size(), "应返回2个商品");
        }
    }

    @Nested
    @DisplayName("listByPriceRange 方法测试")
    class ListByPriceRangeTests {

        @Test
        @DisplayName("应该返回价格区间内的商品")
        void should_listByPriceRange() {
            // Given
            BigDecimal minPrice = BigDecimal.valueOf(50);
            BigDecimal maxPrice = BigDecimal.valueOf(150);
            
            Product product1 = createTestProduct(1L, "商品1", BigDecimal.valueOf(100));
            Product product2 = createTestProduct(2L, "商品2", BigDecimal.valueOf(80));
            
            when(productMapper.selectByPriceRange(minPrice, maxPrice)).thenReturn(Arrays.asList(product1, product2));

            // When
            List<Product> result = productService.listByPriceRange(minPrice, maxPrice);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.size(), "应返回2个商品");
        }
    }

    // ========== 辅助方法 ==========

    private Product createTestProduct(Long id, String name, BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setNum(100);
        product.setSales(0);
        product.setMerchantId(1L);
        product.setCategoryId(1L);
        product.setEnabled(true);
        return product;
    }
}
