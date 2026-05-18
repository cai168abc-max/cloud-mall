package com.atguigu.user.controller;

import com.atguigu.common.bean.UserAddress;
import com.atguigu.user.mapper.UserAccountMapper;
import com.atguigu.user.mapper.UserAddressMapper;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 集成测试类
 * 测试目的：
 * 1. 验证用户注册、登录、信息更新等API的完整业务流程
 * 2. 验证Controller层与Service层、Mapper层的协作是否正常
 * 3. 验证数据库操作的正确性
 * 4. 验证Redis缓存操作的正确性
 * 使用Testcontainers提供MySQL和Redis容器化测试环境
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("UserController 集成测试")
@Disabled("需要Docker环境运行Testcontainers，仅在本地手动运行")
class UserControllerIntegrationTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("cloudtry_user_test")
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
        
        // JWT配置
        registry.add("app.jwt.secret", () -> "dGVzdFNlY3JldEtleUZvckpXVFRlc3RpbmdQdXJwb3Nlcw==");
        registry.add("app.jwt.expire-seconds", () -> "3600");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private UserAddressMapper userAddressMapper;

    private static String accessToken;
    private static Long testUserId;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        // 注意：实际项目中应该使用@Transactional回滚或专用测试数据库
    }

    // ==================== 用户注册测试 ====================

    @Nested
    @DisplayName("用户注册API测试")
    class RegisterTests {

        @Test
        @DisplayName("应该成功注册手机号用户")
        void should_registerPhoneUser_successfully() throws Exception {
            // Given
            String phone = "13800000001";
            String password = "Test@123456";

            // When & Then
            mockMvc.perform(post("/api/user/register")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("注册成功"));
        }

        @Test
        @DisplayName("应该成功注册邮箱用户")
        void should_registerEmailUser_successfully() throws Exception {
            // Given
            String email = "test@example.com";
            String password = "Test@123456";

            // When & Then
            mockMvc.perform(post("/api/user/register")
                            .param("account", email)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("注册成功"));
        }

        @Test
        @DisplayName("应该拒绝重复账号注册")
        void should_rejectDuplicateAccount() throws Exception {
            // Given - 先注册一个用户
            String phone = "13800000002";
            String password = "Test@123456";
            
            mockMvc.perform(post("/api/user/register")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk());

            // When & Then - 尝试重复注册
            mockMvc.perform(post("/api/user/register")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("应该拒绝空账号注册")
        void should_rejectEmptyAccount() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/user/register")
                            .param("account", "")
                            .param("password", "Test@123456")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("应该拒绝空密码注册")
        void should_rejectEmptyPassword() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/user/register")
                            .param("account", "13800000003")
                            .param("password", "")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 商家注册测试 ====================

    @Nested
    @DisplayName("商家注册API测试")
    class RegisterMerchantTests {

        @Test
        @DisplayName("应该成功注册商家账号")
        void should_registerMerchant_successfully() throws Exception {
            // Given
            String phone = "13900000001";
            String password = "Test@123456";
            String merchantName = "测试商家";

            // When & Then
            mockMvc.perform(post("/api/user/registerMerchant")
                            .param("account", phone)
                            .param("password", password)
                            .param("merchantName", merchantName)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("商家注册成功，等待审核"))
                    .andExpect(jsonPath("$.data.role").value("MERCHANT"))
                    .andExpect(jsonPath("$.data.merchantName").value(merchantName))
                    .andExpect(jsonPath("$.data.verified").value(false));
        }
    }

    // ==================== 用户登录测试 ====================

    @Nested
    @DisplayName("用户登录API测试")
    class LoginTests {

        @Test
        @DisplayName("应该成功登录手机号用户")
        void should_loginPhoneUser_successfully() throws Exception {
            // Given - 先注册用户
            String phone = "13800000010";
            String password = "Test@123456";
            
            mockMvc.perform(post("/api/user/register")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk());

            // When - 登录
            MvcResult result = mockMvc.perform(post("/api/user/login")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("登录成功"))
                    .andExpect(jsonPath("$.data.accessToken").exists())
                    .andReturn();

            // Then - 保存token供后续测试使用
            String response = result.getResponse().getContentAsString();
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            accessToken = (String) data.get("accessToken");
        }

        @Test
        @DisplayName("应该成功登录邮箱用户")
        void should_loginEmailUser_successfully() throws Exception {
            // Given - 先注册用户
            String email = "login@example.com";
            String password = "Test@123456";
            
            mockMvc.perform(post("/api/user/register")
                            .param("account", email)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk());

            // When & Then - 登录
            mockMvc.perform(post("/api/user/login")
                            .param("account", email)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.accessToken").exists());
        }

        @Test
        @DisplayName("应该拒绝错误密码登录")
        void should_rejectWrongPassword() throws Exception {
            // Given - 先注册用户
            String phone = "13800000011";
            String password = "Test@123456";
            
            mockMvc.perform(post("/api/user/register")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk());

            // When & Then - 使用错误密码登录
            mockMvc.perform(post("/api/user/login")
                            .param("account", phone)
                            .param("password", "WrongPassword")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("应该拒绝不存在的用户登录")
        void should_rejectNonExistentUser() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/user/login")
                            .param("account", "13899999999")
                            .param("password", "Test@123456")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 用户信息测试 ====================

    @Nested
    @DisplayName("用户信息API测试")
    class UserInfoTests {

        @Test
        @DisplayName("应该获取当前用户信息")
        void should_getCurrentUser() throws Exception {
            // Given - 注册并登录
            String phone = "13800000020";
            String password = "Test@123456";
            
            mockMvc.perform(post("/api/user/register")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk());

            MvcResult loginResult = mockMvc.perform(post("/api/user/login")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andReturn();

            String response = loginResult.getResponse().getContentAsString();
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            String token = (String) data.get("accessToken");

            // When & Then - 获取用户信息
            mockMvc.perform(get("/api/user/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.phone").value(phone));
        }

        @Test
        @DisplayName("应该拒绝未登录用户获取信息")
        void should_rejectUnauthenticatedUser() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/user/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== 地址管理测试 ====================

    @Nested
    @DisplayName("地址管理API测试")
    class AddressTests {

        private String userToken;

        @BeforeEach
        void setUp() throws Exception {
            // 注册并登录用户
            String phone = "13800000030";
            String password = "Test@123456";
            
            mockMvc.perform(post("/api/user/register")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk());

            MvcResult loginResult = mockMvc.perform(post("/api/user/login")
                            .param("account", phone)
                            .param("password", password)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andReturn();

            String response = loginResult.getResponse().getContentAsString();
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            userToken = (String) data.get("accessToken");
        }

        @Test
        @DisplayName("应该成功添加地址")
        void should_addAddress_successfully() throws Exception {
            // Given
            UserAddress address = createTestAddress(null, "测试地址详情");
            String addressJson = objectMapper.writeValueAsString(address);

            // When & Then
            mockMvc.perform(post("/api/user/address")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(addressJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("添加地址成功"));
        }

        @Test
        @DisplayName("应该获取用户地址列表")
        void should_getAddressList() throws Exception {
            // Given - 先添加一个地址
            UserAddress address = createTestAddress(null, "测试地址详情");
            String addressJson = objectMapper.writeValueAsString(address);
            
            mockMvc.perform(post("/api/user/address")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(addressJson))
                    .andExpect(status().isOk());

            // When & Then - 获取地址列表
            mockMvc.perform(get("/api/user/address")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("应该成功更新地址")
        void should_updateAddress_successfully() throws Exception {
            // Given - 先添加一个地址
            UserAddress address = createTestAddress(null, "原始地址");
            String addressJson = objectMapper.writeValueAsString(address);
            
            mockMvc.perform(post("/api/user/address")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(addressJson))
                    .andExpect(status().isOk());

            // When - 更新地址
            UserAddress updatedAddress = createTestAddress(1L, "更新后的地址");
            String updatedJson = objectMapper.writeValueAsString(updatedAddress);

            // Then
            mockMvc.perform(put("/api/user/address/1")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatedJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("修改地址成功"));
        }

        @Test
        @DisplayName("应该成功删除地址")
        void should_deleteAddress_successfully() throws Exception {
            // Given - 先添加一个地址
            UserAddress address = createTestAddress(null, "待删除地址");
            String addressJson = objectMapper.writeValueAsString(address);
            
            mockMvc.perform(post("/api/user/address")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(addressJson))
                    .andExpect(status().isOk());

            // When & Then - 删除地址
            mockMvc.perform(delete("/api/user/address/1")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("删除地址成功"));
        }

        private UserAddress createTestAddress(Long id, String detail) {
            UserAddress address = new UserAddress();
            address.setId(id);
            address.setConsignee("测试收货人");
            address.setPhone("13812345678");
            address.setProvince("北京市");
            address.setCity("北京市");
            address.setDistrict("朝阳区");
            address.setDetail(detail);
            return address;
        }
    }

    // ==================== 密码管理测试 ====================

    @Nested
    @DisplayName("密码管理API测试")
    class PasswordTests {

        @Test
        @DisplayName("应该成功修改密码")
        void should_updatePassword_successfully() throws Exception {
            // Given - 注册并登录
            String phone = "13800000040";
            String oldPassword = "OldPass@123";
            String newPassword = "NewPass@456";
            
            mockMvc.perform(post("/api/user/register")
                            .param("account", phone)
                            .param("password", oldPassword)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk());

            MvcResult loginResult = mockMvc.perform(post("/api/user/login")
                            .param("account", phone)
                            .param("password", oldPassword)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andReturn();

            String response = loginResult.getResponse().getContentAsString();
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            String token = (String) data.get("accessToken");

            // When & Then - 修改密码
            mockMvc.perform(post("/api/user/updatePassword")
                            .header("Authorization", "Bearer " + token)
                            .param("oldPassword", oldPassword)
                            .param("newPassword", newPassword)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("密码修改成功"));

            // Then - 使用新密码登录
            mockMvc.perform(post("/api/user/login")
                            .param("account", phone)
                            .param("password", newPassword)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk());
        }
    }
}
