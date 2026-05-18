package com.atguigu.order.service.impl;

import com.atguigu.order.bean.Cart;
import com.atguigu.order.bean.CartItem;
import com.atguigu.order.feign.ProductFeign;
import com.atguigu.product.bean.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CartServiceImpl 单元测试类
 * 测试购物车服务相关业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartServiceImpl 单元测试")
class CartServiceImplTest {

    @Mock
    private ProductFeign productFeign;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private CartServiceImpl cartService;

    private static final String CART_KEY_PREFIX = "cart:";

    @Nested
    @DisplayName("addItemToCart 方法测试")
    class AddItemToCartTests {

        @Test
        @DisplayName("应该拒绝数量为null或小于等于0的商品")
        void should_rejectInvalidQuantity() {
            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> cartService.addItemToCart(1L, 1L, null));
            assertThrows(IllegalArgumentException.class, 
                () -> cartService.addItemToCart(1L, 1L, 0));
            assertThrows(IllegalArgumentException.class, 
                () -> cartService.addItemToCart(1L, 1L, -1));
        }

        @Test
        @DisplayName("应该成功添加商品到购物车")
        void should_addItemToCart_successfully() throws InterruptedException {
            // Given
            Long userId = 1L;
            Long productId = 1L;
            Integer quantity = 2;
            
            Product product = createTestProduct(productId, BigDecimal.valueOf(100));
            Cart existingCart = new Cart();
            existingCart.setUserId(userId);
            existingCart.setItems(new ArrayList<>());
            
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(startsWith("hot_product:"))).thenReturn(null);
            when(productFeign.getProductById(productId)).thenReturn(product);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(existingCart);

            // When
            Cart result = cartService.addItemToCart(userId, productId, quantity);

            // Then
            assertNotNull(result, "应返回购物车");
            assertEquals(1, result.getItems().size(), "应有1个商品");
            assertEquals(quantity, result.getItems().get(0).getQuantity(), "数量应正确");
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("应该拒绝不存在的商品")
        void should_rejectNonExistentProduct() throws InterruptedException {
            // Given
            Long userId = 1L;
            long productId = 999L;
            Integer quantity = 1;
            
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(startsWith("hot_product:"))).thenReturn(null);
            when(productFeign.getProductById(productId)).thenReturn(null);

            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> cartService.addItemToCart(userId, productId, quantity));
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("应该拒绝库存不足的商品")
        void should_rejectInsufficientStock() throws InterruptedException {
            // Given
            Long userId = 1L;
            Long productId = 1L;
            Integer quantity = 100; // 请求100个
            
            Product product = createTestProduct(productId, BigDecimal.valueOf(100)); // 库存只有10
            
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(startsWith("hot_product:"))).thenReturn(null);
            when(productFeign.getProductById(productId)).thenReturn(product);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> cartService.addItemToCart(userId, productId, quantity));
            assertTrue(exception.getMessage().contains("库存不足"));
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("应该合并已存在商品的数量")
        void should_mergeExistingItemQuantity() throws InterruptedException {
            // Given
            Long userId = 1L;
            Long productId = 1L;
            Integer existingQuantity = 2;
            Integer newQuantity = 3;
            
            Product product = createTestProduct(productId, BigDecimal.valueOf(100));
            
            CartItem existingItem = new CartItem();
            existingItem.setProductId(productId);
            existingItem.setProductName("测试商品");
            existingItem.setPrice(BigDecimal.valueOf(100));
            existingItem.setQuantity(existingQuantity);
            existingItem.setChecked(true);
            
            Cart existingCart = new Cart();
            existingCart.setUserId(userId);
            existingCart.setItems(new ArrayList<>(List.of(existingItem)));
            
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(startsWith("hot_product:"))).thenReturn(null);
            when(productFeign.getProductById(productId)).thenReturn(product);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(existingCart);

            // When
            Cart result = cartService.addItemToCart(userId, productId, newQuantity);

            // Then
            assertEquals(1, result.getItems().size(), "应有1个商品");
            assertEquals(existingQuantity + newQuantity, result.getItems().get(0).getQuantity(), 
                "数量应合并");
        }

        @Test
        @DisplayName("应该拒绝获取锁失败的情况")
        void should_rejectWhenLockFailed() throws InterruptedException {
            // Given
            Long userId = 1L;
            Long productId = 1L;
            Integer quantity = 1;
            
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

            // When & Then
            assertThrows(IllegalStateException.class, 
                () -> cartService.addItemToCart(userId, productId, quantity));
        }
    }

    @Nested
    @DisplayName("removeItemFromCart 方法测试")
    class RemoveItemFromCartTests {

        @Test
        @DisplayName("应该成功移除商品")
        void should_removeItemFromCart_successfully() throws InterruptedException {
            // Given
            Long userId = 1L;
            Long productId = 1L;

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            CartItem item1 = new CartItem();
            item1.setProductId(productId);
            item1.setQuantity(2);
            
            CartItem item2 = new CartItem();
            item2.setProductId(2L);
            item2.setQuantity(1);
            
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>(Arrays.asList(item1, item2)));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(cart);

            // When
            Cart result = cartService.removeItemFromCart(userId, productId);

            // Then
            assertEquals(1, result.getItems().size(), "应剩1个商品");
            assertEquals(2L, result.getItems().get(0).getProductId(), "剩余商品ID应正确");
        }

        @Test
        @DisplayName("应该处理空购物车")
        void should_handleEmptyCart() throws InterruptedException {
            // Given
            Long userId = 1L;
            Long productId = 1L;

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>());
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(cart);

            // When
            Cart result = cartService.removeItemFromCart(userId, productId);

            // Then
            assertNotNull(result, "应返回购物车");
            assertTrue(result.getItems().isEmpty(), "购物车应为空");
        }
    }

    @Nested
    @DisplayName("updateItemQuantity 方法测试")
    class UpdateItemQuantityTests {

        @Test
        @DisplayName("应该拒绝无效数量")
        void should_rejectInvalidQuantity() {
            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> cartService.updateItemQuantity(1L, 1L, null));
            assertThrows(IllegalArgumentException.class, 
                () -> cartService.updateItemQuantity(1L, 1L, 0));
        }

        @Test
        @DisplayName("应该成功更新商品数量")
        void should_updateItemQuantity_successfully() throws InterruptedException {
            // Given
            Long userId = 1L;
            Long productId = 1L;
            Integer newQuantity = 5;

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            Product product = createTestProduct(productId, BigDecimal.valueOf(100));
            
            CartItem item = new CartItem();
            item.setProductId(productId);
            item.setQuantity(2);
            
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>(List.of(item)));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(cart);
            when(valueOperations.get(startsWith("hot_product:"))).thenReturn(null);
            when(productFeign.getProductById(productId)).thenReturn(product);

            // When
            Cart result = cartService.updateItemQuantity(userId, productId, newQuantity);

            // Then
            assertEquals(newQuantity, result.getItems().get(0).getQuantity(), "数量应更新");
        }

        @Test
        @DisplayName("应该拒绝库存不足的更新")
        void should_rejectInsufficientStockUpdate() throws InterruptedException {
            // Given
            Long userId = 1L;
            Long productId = 1L;
            Integer newQuantity = 100;

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            Product product = createTestProduct(productId, BigDecimal.valueOf(100));
            
            CartItem item = new CartItem();
            item.setProductId(productId);
            item.setQuantity(2);
            
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>(List.of(item)));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(cart);
            when(valueOperations.get(startsWith("hot_product:"))).thenReturn(null);
            when(productFeign.getProductById(productId)).thenReturn(product);

            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> cartService.updateItemQuantity(userId, productId, newQuantity));
        }
    }

    @Nested
    @DisplayName("updateItemChecked 方法测试")
    class UpdateItemCheckedTests {

        @Test
        @DisplayName("应该成功更新商品选中状态")
        void should_updateItemChecked_successfully() throws InterruptedException {
            // Given
            Long userId = 1L;
            Long productId = 1L;
            Boolean checked = false;

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            CartItem item = new CartItem();
            item.setProductId(productId);
            item.setChecked(true);
            
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>(List.of(item)));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(cart);

            // When
            Cart result = cartService.updateItemChecked(userId, productId, checked);

            // Then
            assertEquals(checked, result.getItems().get(0).getChecked(), "选中状态应更新");
        }
    }

    @Nested
    @DisplayName("updateAllItemsChecked 方法测试")
    class UpdateAllItemsCheckedTests {

        @Test
        @DisplayName("应该成功更新所有商品选中状态")
        void should_updateAllItemsChecked_successfully() throws InterruptedException {
            // Given
            Long userId = 1L;
            Boolean checked = true;

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            CartItem item1 = new CartItem();
            item1.setProductId(1L);
            item1.setChecked(false);
            
            CartItem item2 = new CartItem();
            item2.setProductId(2L);
            item2.setChecked(false);
            
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>(Arrays.asList(item1, item2)));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(cart);

            // When
            Cart result = cartService.updateAllItemsChecked(userId, checked);

            // Then
            assertTrue(result.getItems().stream().allMatch(CartItem::getChecked), 
                "所有商品应被选中");
        }
    }

    @Nested
    @DisplayName("clearCart 方法测试")
    class ClearCartTests {

        @Test
        @DisplayName("应该成功清空购物车")
        void should_clearCart_successfully() throws InterruptedException {
            // Given
            Long userId = 1L;

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            when(redisTemplate.delete(CART_KEY_PREFIX + userId)).thenReturn(true);

            // When
            Cart result = cartService.clearCart(userId);

            // Then
            assertNotNull(result, "应返回购物车");
            assertTrue(result.getItems().isEmpty(), "购物车应为空");
            verify(redisTemplate).delete(CART_KEY_PREFIX + userId);
        }
    }

    @Nested
    @DisplayName("getCart 方法测试")
    class GetCartTests {

        @Test
        @DisplayName("应该返回购物车")
        void should_returnCart() {
            // Given
            Long userId = 1L;
            
            CartItem item = new CartItem();
            item.setProductId(1L);
            item.setQuantity(2);
            
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>(List.of(item)));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(cart);

            // When
            Cart result = cartService.getCart(userId);

            // Then
            assertNotNull(result, "应返回购物车");
            assertEquals(1, result.getItems().size(), "应有1个商品");
        }

        @Test
        @DisplayName("应该返回空购物车当不存在时")
        void should_returnEmptyCart_whenNotExist() {
            // Given
            Long userId = 1L;
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(null);

            // When
            Cart result = cartService.getCart(userId);

            // Then
            assertNotNull(result, "应返回购物车");
            assertTrue(result.getItems().isEmpty(), "购物车应为空");
        }
    }

    @Nested
    @DisplayName("getCheckedItems 方法测试")
    class GetCheckedItemsTests {

        @Test
        @DisplayName("应该返回选中的商品")
        void should_returnCheckedItems() {
            // Given
            Long userId = 1L;
            
            CartItem item1 = new CartItem();
            item1.setProductId(1L);
            item1.setChecked(true);
            
            CartItem item2 = new CartItem();
            item2.setProductId(2L);
            item2.setChecked(false);
            
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>(Arrays.asList(item1, item2)));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(cart);

            // When
            List<CartItem> result = cartService.getCheckedItems(userId);

            // Then
            assertEquals(1, result.size(), "应返回1个选中商品");
            assertEquals(1L, result.get(0).getProductId(), "选中商品ID应正确");
        }

        @Test
        @DisplayName("应该返回空列表当购物车为空")
        void should_returnEmptyList_whenCartEmpty() {
            // Given
            Long userId = 1L;
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(null);

            // When
            List<CartItem> result = cartService.getCheckedItems(userId);

            // Then
            assertNotNull(result, "结果不应为空");
            assertTrue(result.isEmpty(), "应返回空列表");
        }
    }

    @Nested
    @DisplayName("getCartItemCount 方法测试")
    class GetCartItemCountTests {

        @Test
        @DisplayName("应该返回购物车商品总数量")
        void should_returnCartItemCount() {
            // Given
            Long userId = 1L;
            
            CartItem item1 = new CartItem();
            item1.setProductId(1L);
            item1.setQuantity(2);
            
            CartItem item2 = new CartItem();
            item2.setProductId(2L);
            item2.setQuantity(3);
            
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>(Arrays.asList(item1, item2)));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(cart);

            // When
            Integer result = cartService.getCartItemCount(userId);

            // Then
            assertEquals(5, result, "总数量应为5");
        }

        @Test
        @DisplayName("应该返回0当购物车为空")
        void should_returnZero_whenCartEmpty() {
            // Given
            Long userId = 1L;
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(CART_KEY_PREFIX + userId)).thenReturn(null);

            // When
            Integer result = cartService.getCartItemCount(userId);

            // Then
            assertEquals(0, result, "空购物车应返回0");
        }
    }

    // ========== 辅助方法 ==========

    private Product createTestProduct(Long id, BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setName("测试商品");
        product.setPrice(price);
        product.setNum(10);
        product.setMerchantId(1L);
        return product;
    }
}
