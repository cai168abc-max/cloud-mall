package com.atguigu.order.service.impl;

import com.atguigu.common.enums.OrderStatus;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.common.service.IdempotencyService;
import com.atguigu.order.bean.CartItem;
import com.atguigu.order.bean.Coupon;
import com.atguigu.order.bean.Order;
import com.atguigu.order.bean.OrderItem;
import com.atguigu.order.bean.VirtualAccountLog;
import com.atguigu.order.feign.ProductFeign;
import com.atguigu.order.mapper.OrderItemMapper;
import com.atguigu.order.mapper.OrderMapper;
import com.atguigu.order.service.CartService;
import com.atguigu.order.service.CouponService;
import com.atguigu.order.service.VirtualAccountService;
import com.atguigu.product.bean.Product;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderServiceImpl 单元测试类
 * 测试订单服务相关业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl 单元测试")
class OrderServiceImplTest {

    @Mock
    private ProductFeign productFeign;

    @Mock
    private CouponService couponService;

    @Mock
    private CartService cartService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private VirtualAccountService virtualAccountService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Nested
    @DisplayName("getOrderById 方法测试")
    class GetOrderByIdTests {

        @Test
        @DisplayName("应该返回订单信息")
        void should_returnOrder() {
            // Given
            Long orderId = 1L;
            Order order = createTestOrder(orderId, 1L, 1L, OrderStatus.CREATED);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When
            Order result = orderService.getOrderById(orderId);

            // Then
            assertNotNull(result, "应返回订单");
            assertEquals(orderId, result.getId(), "订单ID应正确");
        }

        @Test
        @DisplayName("应该返回null当订单不存在")
        void should_returnNull_whenOrderNotExist() {
            // Given
            Long orderId = 999L;
            when(orderMapper.selectById(orderId)).thenReturn(null);

            // When
            Order result = orderService.getOrderById(orderId);

            // Then
            assertNull(result, "不存在的订单应返回null");
        }
    }

    @Nested
    @DisplayName("listOrdersByUserId 方法测试（分页）")
    class ListOrdersByUserIdTests {

        @Test
        @DisplayName("应该分页查询用户订单")
        void should_listOrdersByUserId() {
            // Given
            Long userId = 1L;
            int pageNum = 1;
            int pageSize = 10;
            
            Order order1 = createTestOrder(1L, userId, 1L, OrderStatus.CREATED);
            Order order2 = createTestOrder(2L, userId, 1L, OrderStatus.PAID);
            
            Page<Order> mockPage = new Page<>(pageNum, pageSize);
            mockPage.setRecords(Arrays.asList(order1, order2));
            mockPage.setTotal(2);
            
            when(orderMapper.selectByUserId(any(Page.class), eq(userId))).thenReturn(mockPage);

            // When
            IPage<Order> result = orderService.listOrdersByUserId(userId, pageNum, pageSize);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.getRecords().size(), "应返回2个订单");
            assertEquals(2, result.getTotal(), "总数应为2");
        }
    }

    @Nested
    @DisplayName("payOrder 方法测试")
    class PayOrderTests {

        @Test
        @DisplayName("应该成功支付订单")
        void should_payOrder_successfully() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            BigDecimal payAmount = BigDecimal.valueOf(100);
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.CREATED);
            order.setPayAmount(payAmount);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);
            VirtualAccountLog payLog = new VirtualAccountLog();
            when(virtualAccountService.pay(eq(userId), eq(payAmount), eq(orderId), isNull())).thenReturn(payLog);
            when(orderMapper.updateStatusToPaid(orderId, OrderStatus.PAID.name())).thenReturn(1);

            // When
            Order result = orderService.payOrder(orderId, userId);

            // Then
            assertNotNull(result, "应返回订单");
            assertEquals(OrderStatus.PAID, result.getStatus(), "订单状态应为已支付");
            verify(virtualAccountService).pay(userId, payAmount, orderId, null);
        }

        @Test
        @DisplayName("应该拒绝不存在的订单支付")
        void should_rejectNonExistentOrder() {
            // Given
            Long orderId = 999L;
            Long userId = 1L;
            
            when(orderMapper.selectById(orderId)).thenReturn(null);

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                () -> orderService.payOrder(orderId, userId));
            assertEquals("订单不存在", exception.getMessage());
        }

        @Test
        @DisplayName("应该拒绝状态不正确的订单支付")
        void should_rejectInvalidStatusOrder() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.PAID);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                () -> orderService.payOrder(orderId, userId));
            assertEquals("订单状态异常，无法支付", exception.getMessage());
        }

        @Test
        @DisplayName("应该拒绝非订单所有者支付")
        void should_rejectNonOwnerPayment() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            Long otherUserId = 2L;
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.CREATED);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When & Then
            assertThrows(SecurityException.class, 
                () -> orderService.payOrder(orderId, otherUserId));
        }
    }

    @Nested
    @DisplayName("shipOrder 方法测试")
    class ShipOrderTests {

        @Test
        @DisplayName("应该成功发货")
        void should_shipOrder_successfully() {
            // Given
            Long orderId = 1L;
            Long merchantId = 1L;
            
            Order order = createTestOrder(orderId, 1L, merchantId, OrderStatus.PAID);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);
            when(orderMapper.updateStatusToShipped(orderId, OrderStatus.SHIPPED.name())).thenReturn(1);

            // When
            Order result = orderService.shipOrder(orderId, merchantId);

            // Then
            assertNotNull(result, "应返回订单");
            assertEquals(OrderStatus.SHIPPED, result.getStatus(), "订单状态应为已发货");
        }

        @Test
        @DisplayName("应该返回null当订单不存在或状态不正确")
        void should_returnNull_whenOrderNotExistOrWrongStatus() {
            // Given
            Long orderId = 1L;
            Long merchantId = 1L;
            
            Order order = createTestOrder(orderId, 1L, merchantId, OrderStatus.CREATED);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When
            Order result = orderService.shipOrder(orderId, merchantId);

            // Then
            assertNull(result, "状态不正确应返回null");
        }

        @Test
        @DisplayName("应该拒绝非订单所属商家发货")
        void should_rejectNonMerchantShipment() {
            // Given
            Long orderId = 1L;
            Long merchantId = 1L;
            Long otherMerchantId = 2L;
            
            Order order = createTestOrder(orderId, 1L, merchantId, OrderStatus.PAID);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When & Then
            assertThrows(SecurityException.class, 
                () -> orderService.shipOrder(orderId, otherMerchantId));
        }
    }

    @Nested
    @DisplayName("completeOrder 方法测试")
    class CompleteOrderTests {

        @Test
        @DisplayName("应该成功完成订单")
        void should_completeOrder_successfully() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.SHIPPED);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);
            when(orderMapper.updateStatusToCompleted(orderId, OrderStatus.COMPLETED.name())).thenReturn(1);

            // When
            Order result = orderService.completeOrder(orderId, userId);

            // Then
            assertNotNull(result, "应返回订单");
            assertEquals(OrderStatus.COMPLETED, result.getStatus(), "订单状态应为已完成");
        }

        @Test
        @DisplayName("应该拒绝非订单所有者确认收货")
        void should_rejectNonOwnerCompletion() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            Long otherUserId = 2L;
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.SHIPPED);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When & Then
            assertThrows(SecurityException.class, 
                () -> orderService.completeOrder(orderId, otherUserId));
        }
    }

    @Nested
    @DisplayName("cancelOrder 方法测试")
    class CancelOrderTests {

        @Test
        @DisplayName("应该成功取消订单")
        void should_cancelOrder_successfully() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.CREATED);
            OrderItem orderItem = createTestOrderItem(1L, orderId, 1L, 1);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);
            when(orderItemMapper.selectByOrderId(orderId)).thenReturn(Arrays.asList(orderItem));
            when(productFeign.batchIncreaseStock(anyList())).thenReturn(1);
            when(orderMapper.updateStatus(orderId, OrderStatus.CANCELED.name())).thenReturn(1);

            // When
            Order result = orderService.cancelOrder(orderId, userId);

            // Then
            assertNotNull(result, "应返回订单");
            assertEquals(OrderStatus.CANCELED, result.getStatus(), "订单状态应为已取消");
            verify(productFeign).batchIncreaseStock(anyList());
        }

        @Test
        @DisplayName("应该返回null当订单不存在或状态不正确")
        void should_returnNull_whenOrderNotExistOrWrongStatus() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.PAID);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When
            Order result = orderService.cancelOrder(orderId, userId);

            // Then
            assertNull(result, "状态不正确应返回null");
        }

        @Test
        @DisplayName("应该拒绝非订单所有者取消")
        void should_rejectNonOwnerCancellation() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            Long otherUserId = 2L;
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.CREATED);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When & Then
            assertThrows(SecurityException.class, 
                () -> orderService.cancelOrder(orderId, otherUserId));
        }
    }

    @Nested
    @DisplayName("applyRefund 方法测试")
    class ApplyRefundTests {

        @Test
        @DisplayName("应该成功申请退款")
        void should_applyRefund_successfully() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.PAID);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);
            when(orderMapper.updateStatus(orderId, OrderStatus.REFUNDING.name())).thenReturn(1);

            // When
            Order result = orderService.applyRefund(orderId, userId);

            // Then
            assertNotNull(result, "应返回订单");
            assertEquals(OrderStatus.REFUNDING, result.getStatus(), "订单状态应为退款中");
        }

        @Test
        @DisplayName("应该拒绝非订单所有者申请退款")
        void should_rejectNonOwnerRefund() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            Long otherUserId = 2L;
            
            Order order = createTestOrder(orderId, userId, 1L, OrderStatus.PAID);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When & Then
            assertThrows(SecurityException.class, 
                () -> orderService.applyRefund(orderId, otherUserId));
        }
    }

    @Nested
    @DisplayName("approveRefund 方法测试")
    class ApproveRefundTests {

        @Test
        @DisplayName("应该成功批准退款")
        void should_approveRefund_successfully() {
            // Given
            Long orderId = 1L;
            Long merchantId = 1L;
            Long userId = 1L;
            BigDecimal payAmount = BigDecimal.valueOf(100);
            
            Order order = createTestOrder(orderId, userId, merchantId, OrderStatus.REFUNDING);
            order.setPayAmount(payAmount);
            OrderItem orderItem = createTestOrderItem(1L, orderId, 1L, 1);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);
            when(orderItemMapper.selectByOrderId(orderId)).thenReturn(Arrays.asList(orderItem));
            when(productFeign.batchIncreaseStock(anyList())).thenReturn(1);
            VirtualAccountLog refundLog = new VirtualAccountLog();
            when(virtualAccountService.refund(eq(userId), eq(payAmount), eq(orderId), isNull())).thenReturn(refundLog);
            when(orderMapper.updateStatus(orderId, OrderStatus.REFUNDED.name())).thenReturn(1);

            // When
            boolean result = orderService.approveRefund(orderId, merchantId);

            // Then
            assertTrue(result, "批准退款应成功");
            verify(virtualAccountService).refund(userId, payAmount, orderId, null);
        }

        @Test
        @DisplayName("应该拒绝非订单所属商家批准退款")
        void should_rejectNonMerchantApproval() {
            // Given
            Long orderId = 1L;
            Long merchantId = 1L;
            Long otherMerchantId = 2L;
            
            Order order = createTestOrder(orderId, 1L, merchantId, OrderStatus.REFUNDING);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When & Then
            assertThrows(SecurityException.class, 
                () -> orderService.approveRefund(orderId, otherMerchantId));
        }
    }

    @Nested
    @DisplayName("rejectRefund 方法测试")
    class RejectRefundTests {

        @Test
        @DisplayName("应该成功拒绝退款")
        void should_rejectRefund_successfully() {
            // Given
            Long orderId = 1L;
            Long merchantId = 1L;
            
            Order order = createTestOrder(orderId, 1L, merchantId, OrderStatus.REFUNDING);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);
            when(orderMapper.updateStatus(orderId, OrderStatus.PAID.name())).thenReturn(1);

            // When
            boolean result = orderService.rejectRefund(orderId, merchantId);

            // Then
            assertTrue(result, "拒绝退款应成功");
        }

        @Test
        @DisplayName("应该拒绝非订单所属商家拒绝退款")
        void should_rejectNonMerchantRejection() {
            // Given
            Long orderId = 1L;
            Long merchantId = 1L;
            Long otherMerchantId = 2L;
            
            Order order = createTestOrder(orderId, 1L, merchantId, OrderStatus.REFUNDING);
            
            when(orderMapper.selectById(orderId)).thenReturn(order);

            // When & Then
            assertThrows(SecurityException.class, 
                () -> orderService.rejectRefund(orderId, otherMerchantId));
        }
    }

    @Nested
    @DisplayName("batchPayOrders 方法测试")
    class BatchPayOrdersTests {

        @Test
        @DisplayName("应该返回空Map当orderIds为空")
        void should_returnEmptyMap_whenOrderIdsIsEmpty() {
            // When
            Map<Long, Order> result = orderService.batchPayOrders(null, 1L);

            // Then
            assertNotNull(result, "结果不应为空");
            assertTrue(result.isEmpty(), "应返回空Map");
        }

        @Test
        @DisplayName("应该批量支付订单")
        void should_batchPayOrders() {
            // Given
            List<Long> orderIds = Arrays.asList(1L, 2L);
            Long userId = 1L;
            
            Order order1 = createTestOrder(1L, userId, 1L, OrderStatus.CREATED);
            Order order2 = createTestOrder(2L, userId, 1L, OrderStatus.CREATED);
            
            when(orderMapper.batchSelectByIdsAndStatus(orderIds, OrderStatus.CREATED.name()))
                .thenReturn(Arrays.asList(order1, order2));
            when(orderMapper.batchUpdateStatusToPaid(anyList(), eq(OrderStatus.PAID.name()))).thenReturn(2);

            // When
            Map<Long, Order> result = orderService.batchPayOrders(orderIds, userId);

            // Then
            assertEquals(2, result.size(), "应返回2个订单");
        }
    }

    @Nested
    @DisplayName("batchGetOrders 方法测试")
    class BatchGetOrdersTests {

        @Test
        @DisplayName("应该批量获取订单")
        void should_batchGetOrders() {
            // Given
            List<Long> orderIds = Arrays.asList(1L, 2L);
            
            Order order1 = createTestOrder(1L, 1L, 1L, OrderStatus.CREATED);
            Order order2 = createTestOrder(2L, 1L, 1L, OrderStatus.PAID);
            
            when(orderMapper.batchSelectByIds(orderIds)).thenReturn(Arrays.asList(order1, order2));

            // When
            List<Order> result = orderService.batchGetOrders(orderIds);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.size(), "应返回2个订单");
        }
    }

    @Nested
    @DisplayName("hasOrdersForProduct 方法测试")
    class HasOrdersForProductTests {

        @Test
        @DisplayName("应该返回true当商品有订单")
        void should_returnTrue_whenProductHasOrders() {
            // Given
            long productId = 1L;
            when(orderItemMapper.countByProductId(productId)).thenReturn(5L);

            // When
            boolean result = orderService.hasOrdersForProduct(productId);

            // Then
            assertTrue(result, "有订单应返回true");
        }

        @Test
        @DisplayName("应该返回false当商品无订单")
        void should_returnFalse_whenProductHasNoOrders() {
            // Given
            long productId = 1L;
            when(orderItemMapper.countByProductId(productId)).thenReturn(0L);

            // When
            boolean result = orderService.hasOrdersForProduct(productId);

            // Then
            assertFalse(result, "无订单应返回false");
        }
    }

    // ========== 辅助方法 ==========

    private Order createTestOrder(Long id, Long userId, Long merchantId, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setStatus(status);
        order.setTotalPrice(BigDecimal.valueOf(100));
        order.setPayAmount(BigDecimal.valueOf(100));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setNickName("测试用户");
        order.setAddress("测试地址");
        return order;
    }

    private OrderItem createTestOrderItem(Long id, Long orderId, Long productId, Integer quantity) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setProductName("测试商品");
        item.setPrice(BigDecimal.valueOf(100));
        item.setQuantity(quantity);
        return item;
    }

    private Product createTestProduct(Long id, String name, BigDecimal price, Integer stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setNum(stock);
        product.setMerchantId(1L);
        return product;
    }
}
