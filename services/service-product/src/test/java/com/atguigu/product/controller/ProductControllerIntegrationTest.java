package com.atguigu.product.controller;

import com.atguigu.common.utils.HmacSignatureUtil;
import com.atguigu.product.bean.Category;
import com.atguigu.product.bean.Product;
import com.atguigu.product.mapper.CategoryMapper;
import com.atguigu.product.mapper.ProductMapper;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProductController 集成测试类
 * 测试目的：
 * 1. 验证商品查询、创建、更新、删除等API的完整业务流程
 * 2. 验证Controller层与Service层、Mapper层的协作是否正常
 * 3. 验证数据库操作的正确性
 * 4. 验证Redis缓存操作的正确性
 * 使用Testcontainers提供MySQL和Redis容器化测试环境
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Disabled("需要Docker环境运行Testcontainers，仅在本地手动运行")
@DisplayName("ProductController 集成测试")
class ProductControllerIntegrationTest {

    /** 与运行环境一致的内部密钥长度要求（≥32） */
    private static final String TEST_INTERNAL_SECRET = "01234567890123456789012345678901";

    private static String xInternal(String path) {
        return HmacSignatureUtil.generateInternalRequestToken(TEST_INTERNAL_SECRET, path);
    }

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("cloudtry_product_test")
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

        registry.add("security.internal.secret", () -> TEST_INTERNAL_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    private static Long testCategoryId;
    private static Long testProductId;

    @BeforeEach
    void setUp() {
        // 初始化测试分类
        if (testCategoryId == null) {
            Category category = new Category();
            category.setName("测试分类");
            category.setParentId(0L);
            category.setLevel(1);
            categoryMapper.insert(category);
            testCategoryId = category.getId();
        }
    }

    // ==================== 商品查询测试 ====================

    @Nested
    @DisplayName("商品查询API测试")
    class QueryProductTests {

        @Test
        @DisplayName("应该成功获取商品详情")
        void should_getProductById_successfully() throws Exception {
            // Given - 创建测试商品
            Product product = createTestProduct("测试商品", BigDecimal.valueOf(99.99), 100);
            productMapper.insertProduct(product);
            Long productId = product.getId();

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.get("/api/product/{id}", productId)
                            .header("X-Internal-Request", xInternal("/api/product/" + productId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(productId))
                    .andExpect(jsonPath("$.data.name").value("测试商品"))
                    .andExpect(jsonPath("$.data.price").value(99.99));
        }

        @Test
        @DisplayName("应该返回404当商品不存在")
        void should_return404_whenProductNotExist() throws Exception {
            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.get("/api/product/{id}", 999999L)
                            .header("X-Internal-Request", xInternal("/api/product/999999"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("商品不存在"));
        }

        @Test
        @DisplayName("应该成功分页查询商品列表")
        void should_listProductsByPage_successfully() throws Exception {
            // Given - 创建多个测试商品
            for (int i = 0; i < 15; i++) {
                Product product = createTestProduct("商品" + i, BigDecimal.valueOf(10 + i), 100);
                productMapper.insertProduct(product);
            }

            // When & Then - 查询第一页
            mockMvc.perform(MockMvcRequestBuilders.get("/api/product")
                            .header("X-Internal-Request", xInternal("/api/product"))
                            .param("page", "1")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records.length()").value(10))
                    .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(15)));
        }

        @Test
        @DisplayName("应该成功查询分类下的商品")
        void should_listByCategory_successfully() throws Exception {
            // Given - 创建分类商品
            Product product = createTestProduct("分类商品", BigDecimal.valueOf(50), 100);
            product.setCategoryId(testCategoryId);
            productMapper.insertProduct(product);

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.get("/api/product/category/{categoryId}", testCategoryId)
                            .header("X-Internal-Request", xInternal("/api/product/category/" + testCategoryId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("应该成功查询有库存商品")
        void should_listInStock_successfully() throws Exception {
            // Given - 创建有库存和无库存商品
            Product inStockProduct = createTestProduct("有库存商品", BigDecimal.valueOf(50), 100);
            Product outOfStockProduct = createTestProduct("无库存商品", BigDecimal.valueOf(50), 0);
            productMapper.insertProduct(inStockProduct);
            productMapper.insertProduct(outOfStockProduct);

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.get("/api/product/inStock")
                            .header("X-Internal-Request", xInternal("/api/product/inStock"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("应该成功查询热门商品")
        void should_listHotProducts_successfully() throws Exception {
            // Given - 创建热门商品
            Product hotProduct = createTestProduct("热门商品", BigDecimal.valueOf(50), 100);
            hotProduct.setSales(1000);
            productMapper.insertProduct(hotProduct);

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.get("/api/product/hot")
                            .header("X-Internal-Request", xInternal("/api/product/hot"))
                            .param("limit", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("应该成功搜索商品")
        void should_searchProducts_successfully() throws Exception {
            // Given - 创建可搜索商品
            Product searchableProduct = createTestProduct("可搜索测试商品", BigDecimal.valueOf(50), 100);
            productMapper.insertProduct(searchableProduct);

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.get("/api/product/search")
                            .header("X-Internal-Request", xInternal("/api/product/search"))
                            .param("keyword", "测试")
                            .param("sortBy", "price")
                            .param("ascending", "true")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ==================== 商品管理测试 ====================

    @Nested
    @DisplayName("商品管理API测试")
    class ManageProductTests {

        @Test
        @DisplayName("应该成功创建商品")
        void should_createProduct_successfully() throws Exception {
            // Given
            Product product = createTestProduct("新商品", BigDecimal.valueOf(199.99), 50);
            String productJson = objectMapper.writeValueAsString(product);

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.post("/api/product/manage/save")
                            .header("X-Internal-Request", xInternal("/api/product/manage/save"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(productJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("商品保存成功"));
        }

        @Test
        @DisplayName("应该成功更新商品")
        void should_updateProduct_successfully() throws Exception {
            // Given - 先创建商品
            Product product = createTestProduct("原始商品", BigDecimal.valueOf(99.99), 100);
            productMapper.insertProduct(product);
            
            // 更新商品信息
            product.setName("更新后的商品");
            product.setPrice(BigDecimal.valueOf(149.99));
            String productJson = objectMapper.writeValueAsString(product);

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.post("/api/product/manage/save")
                            .header("X-Internal-Request", xInternal("/api/product/manage/save"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(productJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("应该成功删除商品")
        void should_deleteProduct_successfully() throws Exception {
            // Given - 先创建商品
            Product product = createTestProduct("待删除商品", BigDecimal.valueOf(99.99), 100);
            productMapper.insertProduct(product);
            Long productId = product.getId();

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.delete("/api/product/manage/{id}", productId)
                            .header("X-Internal-Request", xInternal("/api/product/manage/" + productId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("商品删除成功"));
        }

        @Test
        @DisplayName("应该返回404当删除不存在的商品")
        void should_return404_whenDeleteNonExistentProduct() throws Exception {
            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.delete("/api/product/manage/{id}", 999999L)
                            .header("X-Internal-Request", xInternal("/api/product/manage/999999"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }

        @Test
        @DisplayName("应该成功更新商品价格")
        void should_updatePrice_successfully() throws Exception {
            // Given - 先创建商品
            Product product = createTestProduct("价格测试商品", BigDecimal.valueOf(99.99), 100);
            productMapper.insertProduct(product);
            Long productId = product.getId();

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.put("/api/product/manage/{id}/price", productId)
                            .header("X-Internal-Request", xInternal("/api/product/manage/" + productId + "/price"))
                            .param("price", "199.99")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("价格修改成功"));
        }
    }

    // ==================== 内部API测试 ====================

    @Nested
    @DisplayName("内部API测试")
    class InternalApiTests {

        @Test
        @DisplayName("应该成功通过内部API获取商品")
        void should_getProductById_internalApi() throws Exception {
            // Given - 创建测试商品
            Product product = createTestProduct("内部API测试商品", BigDecimal.valueOf(99.99), 100);
            productMapper.insertProduct(product);
            Long productId = product.getId();

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.get("/internal/api/product/{id}", productId)
                            .header("X-Internal-Request", xInternal("/internal/api/product/" + productId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId))
                    .andExpect(jsonPath("$.name").value("内部API测试商品"));
        }

        @Test
        @DisplayName("应该成功批量获取商品")
        void should_batchGetProducts_successfully() throws Exception {
            // Given - 创建多个测试商品
            List<Long> productIds = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Product product = createTestProduct("批量商品" + i, BigDecimal.valueOf(10 + i), 100);
                productMapper.insertProduct(product);
                productIds.add(product.getId());
            }
            String idsJson = objectMapper.writeValueAsString(productIds);

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.post("/internal/api/product/batch")
                            .header("X-Internal-Request", xInternal("/internal/api/product/batch"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(idsJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(3));
        }

        @Test
        @DisplayName("应该成功扣减库存")
        void should_decreaseStock_successfully() throws Exception {
            // Given - 创建测试商品
            Product product = createTestProduct("库存测试商品", BigDecimal.valueOf(99.99), 100);
            productMapper.insertProduct(product);
            Long productId = product.getId();

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.put("/internal/api/product/decreaseStock")
                            .header("X-Internal-Request", xInternal("/internal/api/product/decreaseStock"))
                            .param("productId", productId.toString())
                            .param("quantity", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(1));
        }

        @Test
        @DisplayName("应该成功增加库存")
        void should_increaseStock_successfully() throws Exception {
            // Given - 创建测试商品
            Product product = createTestProduct("库存增加测试商品", BigDecimal.valueOf(99.99), 100);
            productMapper.insertProduct(product);
            Long productId = product.getId();

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.put("/internal/api/product/increaseStock")
                            .header("X-Internal-Request", xInternal("/internal/api/product/increaseStock"))
                            .param("productId", productId.toString())
                            .param("quantity", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(1));
        }

        @Test
        @DisplayName("应该成功批量增加库存")
        void should_batchIncreaseStock_successfully() throws Exception {
            // Given - 创建测试商品
            Product product = createTestProduct("批量库存商品", BigDecimal.valueOf(99.99), 100);
            productMapper.insertProduct(product);
            Long productId = product.getId();

            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("productId", productId);
            item.put("quantity", 5);
            items.add(item);
            String itemsJson = objectMapper.writeValueAsString(items);

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.put("/internal/api/product/batchIncreaseStock")
                            .header("X-Internal-Request", xInternal("/internal/api/product/batchIncreaseStock"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(itemsJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(1));
        }

        @Test
        @DisplayName("应该成功批量扣减库存")
        void should_batchDecreaseStock_successfully() throws Exception {
            // Given - 创建测试商品
            Product product = createTestProduct("批量扣减库存商品", BigDecimal.valueOf(99.99), 100);
            productMapper.insertProduct(product);
            Long productId = product.getId();

            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("productId", productId);
            item.put("quantity", 5);
            items.add(item);
            String itemsJson = objectMapper.writeValueAsString(items);

            // When & Then
            mockMvc.perform(MockMvcRequestBuilders.put("/internal/api/product/batchDecreaseStock")
                            .header("X-Internal-Request", xInternal("/internal/api/product/batchDecreaseStock"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(itemsJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(1));
        }
    }

    // ==================== 辅助方法 ====================

    private Product createTestProduct(String name, BigDecimal price, Integer stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setNum(stock);
        product.setSales(0);
        product.setMerchantId(1L);
        product.setCategoryId(testCategoryId);
        product.setEnabled(true);
        product.setDescription("测试商品描述");
        product.setImageUrl("https://example.com/image.jpg");
        return product;
    }
}
