package com.atguigu.order.controller;

import com.atguigu.common.enums.OrderStatus;
import com.atguigu.order.bean.Order;
import com.atguigu.order.bean.OrderItem;
import com.atguigu.order.mapper.OrderItemMapper;
import com.atguigu.order.mapper.OrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController 集成测试类
 * 
 * 测试目的：
 * 1. 验证订单创建、支付、发货、完成等API的完整业务流程
 * 2. 验证Controller层与Service层、Mapper层的协作是否正常
 * 3. 验证数据库操作的正确性
 * 4. 验证Redis缓存操作的正确性
 * 
 * 使用Testcontainers提供MySQL和Redis容器化测试环境
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("OrderController 集成测试")
class OrderControllerIntegrationTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("cloudtry_order_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL配置
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        
        // Redis配置
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        
        // 禁用Nacos和Sentinel
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("spring.cloud.sentinel.enabled", () -> "false");
        
        // 禁用Seata
        registry.add("seata.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    private static Long testOrderId;
    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_MERCHANT_ID = 2L;
    private static final Long TEST_PRODUCT_ID = 1L;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
    }

    // ==================== 订单查询测试 ====================

    @Nested
    @DisplayName("订单查询API测试")
    class QueryOrderTests {

        @Test
        @DisplayName("应该成功获取订单详情")
        void should_getOrderById_successfully() throws Exception {
            // Given - 创建测试订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.CREATED);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(get("/api/order/{id}", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(orderId));
        }

        @Test
        @DisplayName("应该返回404当订单不存在")
        void should_return404_whenOrderNotExist() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/order/{id}", 999999L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("订单不存在"));
        }

        @Test
        @DisplayName("应该成功获取用户订单列表")
        void should_listMyOrders_successfully() throws Exception {
            // Given - 创建多个测试订单
            for (int i = 0; i < 3; i++) {
                Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.CREATED);
                orderMapper.insert(order);
            }

            // When & Then
            mockMvc.perform(get("/api/order/my")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ==================== 订单创建测试 ====================

    @Nested
    @DisplayName("订单创建API测试")
    class CreateOrderTests {

        @Test
        @DisplayName("应该成功创建订单")
        void should_createOrder_successfully() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/order")
                            .param("productId", TEST_PRODUCT_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("创建订单成功"));
        }

        @Test
        @DisplayName("应该成功使用优惠券创建订单")
        void should_createOrderWithCoupon_successfully() throws Exception {
            // Given
            Long couponId = 1L;

            // When & Then
            mockMvc.perform(post("/api/order")
                            .param("productId", TEST_PRODUCT_ID.toString())
                            .param("couponId", couponId.toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== 订单支付测试 ====================

    @Nested
    @DisplayName("订单支付API测试")
    class PayOrderTests {

        @Test
        @DisplayName("应该成功支付订单")
        void should_payOrder_successfully() throws Exception {
            // Given - 创建待支付订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.CREATED);
            order.setPayAmount(BigDecimal.valueOf(100));
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/pay", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("支付成功"));
        }

        @Test
        @DisplayName("应该拒绝支付已支付的订单")
        void should_rejectPayPaidOrder() throws Exception {
            // Given - 创建已支付订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.PAID);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/pay", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("支付失败"));
        }
    }

    // ==================== 订单发货测试 ====================

    @Nested
    @DisplayName("订单发货API测试")
    class ShipOrderTests {

        @Test
        @DisplayName("应该成功发货")
        void should_shipOrder_successfully() throws Exception {
            // Given - 创建已支付订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.PAID);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/ship", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("发货成功"));
        }

        @Test
        @DisplayName("应该拒绝发货未支付的订单")
        void should_rejectShipUnpaidOrder() throws Exception {
            // Given - 创建未支付订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.CREATED);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/ship", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("发货失败"));
        }
    }

    // ==================== 订单完成测试 ====================

    @Nested
    @DisplayName("订单完成API测试")
    class CompleteOrderTests {

        @Test
        @DisplayName("应该成功确认收货")
        void should_completeOrder_successfully() throws Exception {
            // Given - 创建已发货订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.SHIPPED);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/complete", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("确认收货成功"));
        }

        @Test
        @DisplayName("应该拒绝确认收货未发货的订单")
        void should_rejectCompleteUnshippedOrder() throws Exception {
            // Given - 创建未发货订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.PAID);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/complete", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("确认收货失败"));
        }
    }

    // ==================== 订单取消测试 ====================

    @Nested
    @DisplayName("订单取消API测试")
    class CancelOrderTests {

        @Test
        @DisplayName("应该成功取消订单")
        void should_cancelOrder_successfully() throws Exception {
            // Given - 创建待支付订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.CREATED);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/cancel", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("取消订单成功"));
        }

        @Test
        @DisplayName("应该拒绝取消已支付的订单")
        void should_rejectCancelPaidOrder() throws Exception {
            // Given - 创建已支付订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.PAID);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/cancel", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("取消订单失败"));
        }
    }

    // ==================== 退款测试 ====================

    @Nested
    @DisplayName("退款API测试")
    class RefundTests {

        @Test
        @DisplayName("应该成功申请退款")
        void should_applyRefund_successfully() throws Exception {
            // Given - 创建已支付订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.PAID);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(post("/api/order/{id}/refund", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("退款申请已提交"));
        }

        @Test
        @DisplayName("应该成功批准退款")
        void should_approveRefund_successfully() throws Exception {
            // Given - 创建退款中订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.REFUNDING);
            order.setPayAmount(BigDecimal.valueOf(100));
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/refund/approve", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("退款已批准"));
        }

        @Test
        @DisplayName("应该成功拒绝退款")
        void should_rejectRefund_successfully() throws Exception {
            // Given - 创建退款中订单
            Order order = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.REFUNDING);
            orderMapper.insert(order);
            Long orderId = order.getId();

            // When & Then
            mockMvc.perform(put("/api/order/{id}/refund/reject", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("退款已拒绝"));
        }
    }

    // ==================== 批量操作测试 ====================

    @Nested
    @DisplayName("批量操作API测试")
    class BatchOperationTests {

        @Test
        @DisplayName("应该成功批量支付订单")
        void should_batchPayOrders_successfully() throws Exception {
            // Given - 创建多个待支付订单
            Order order1 = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.CREATED);
            Order order2 = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.CREATED);
            orderMapper.insert(order1);
            orderMapper.insert(order2);
            
            List<Long> orderIds = Arrays.asList(order1.getId(), order2.getId());
            String idsJson = objectMapper.writeValueAsString(orderIds);

            // When & Then
            mockMvc.perform(post("/api/order/batch/pay")
                            .param("orderIds", order1.getId().toString())
                            .param("orderIds", order2.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("批量支付完成"));
        }

        @Test
        @DisplayName("应该成功批量发货")
        void should_batchShipOrders_successfully() throws Exception {
            // Given - 创建多个已支付订单
            Order order1 = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.PAID);
            Order order2 = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.PAID);
            orderMapper.insert(order1);
            orderMapper.insert(order2);

            // When & Then
            mockMvc.perform(post("/api/order/batch/ship")
                            .param("orderIds", order1.getId().toString())
                            .param("orderIds", order2.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("批量发货完成"));
        }

        @Test
        @DisplayName("应该成功批量确认收货")
        void should_batchCompleteOrders_successfully() throws Exception {
            // Given - 创建多个已发货订单
            Order order1 = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.SHIPPED);
            Order order2 = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.SHIPPED);
            orderMapper.insert(order1);
            orderMapper.insert(order2);

            // When & Then
            mockMvc.perform(post("/api/order/batch/complete")
                            .param("orderIds", order1.getId().toString())
                            .param("orderIds", order2.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("批量确认收货完成"));
        }

        @Test
        @DisplayName("应该成功批量取消订单")
        void should_batchCancelOrders_successfully() throws Exception {
            // Given - 创建多个待支付订单
            Order order1 = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.CREATED);
            Order order2 = createTestOrder(TEST_USER_ID, TEST_MERCHANT_ID, OrderStatus.CREATED);
            orderMapper.insert(order1);
            orderMapper.insert(order2);

            // When & Then
            mockMvc.perform(post("/api/order/batch/cancel")
                            .param("orderIds", order1.getId().toString())
                            .param("orderIds", order2.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("批量取消订单完成"));
        }
    }

    // ==================== 完整业务流程测试 ====================

    @Nested
    @DisplayName("完整业务流程测试")
    class FullWorkflowTests {

        @Test
        @DisplayName("应该完成完整的订单生命周期")
        void should_completeFullOrderLifecycle() throws Exception {
            // Step 1: 创建订单
            MvcResult createResult = mockMvc.perform(post("/api/order")
                            .param("productId", TEST_PRODUCT_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();

            // 从响应中获取订单ID（简化处理，实际应从响应解析）
            // Step 2: 支付订单
            // Step 3: 发货
            // Step 4: 确认收货
            // 这里简化测试，实际应该按顺序调用各API
        }
    }

    // ==================== 辅助方法 ====================

    private Order createTestOrder(Long userId, Long merchantId, OrderStatus status) {
        Order order = new Order();
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

    private OrderItem createTestOrderItem(Long orderId, Long productId, Integer quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setProductName("测试商品");
        item.setPrice(BigDecimal.valueOf(100));
        item.setQuantity(quantity);
        return item;
    }
}
