package com.atguigu.user.service.impl;

import com.atguigu.common.bean.UserAccount;
import com.atguigu.common.bean.UserAddress;
import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.service.RateLimitService;
import com.atguigu.common.service.VerificationCodeService;
import com.atguigu.user.mapper.UserAccountMapper;
import com.atguigu.user.mapper.UserAddressMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserAuthServiceImpl 单元测试类
 * 测试用户认证相关业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAuthServiceImpl 单元测试")
class UserAuthServiceImplTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private UserAddressMapper userAddressMapper;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private VerificationCodeService verificationCodeService;

    @InjectMocks
    private UserAuthServiceImpl userAuthService;

    private static final String JWT_SECRET = "dGVzdFNlY3JldEtleUZvckpXVFRlc3RpbmdQdXJwb3Nlcw==";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userAuthService, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(userAuthService, "expireSeconds", 3600L);
        ReflectionTestUtils.setField(userAuthService, "jwtIssuer", "cloudtry-auth");
        ReflectionTestUtils.setField(userAuthService, "jwtAudience", "cloudtry-api");
    }

    @Nested
    @DisplayName("register 方法测试")
    class RegisterTests {

        @Test
        @DisplayName("应该成功注册手机号用户")
        void should_registerPhoneUser_successfully() {
            // Given
            String phone = "13812345678";
            String password = "password123";
            
            when(userAccountMapper.insertUser(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount account = invocation.getArgument(0);
                account.setId(1L);
                return 1;
            });

            // When
            UserAccount result = userAuthService.register(phone, password);

            // Then
            assertNotNull(result, "注册结果不应为空");
            assertEquals(phone, result.getPhone(), "手机号应正确设置");
            assertNull(result.getEmail(), "邮箱应为空");
            assertEquals(UserRole.USER, result.getRole(), "角色应为普通用户");
            assertTrue(result.getEnabled(), "账号应启用");
            assertNotNull(result.getPasswordHash(), "密码哈希应存在");
            verify(userAccountMapper).insertUser(any(UserAccount.class));
        }

        @Test
        @DisplayName("应该成功注册邮箱用户")
        void should_registerEmailUser_successfully() {
            // Given
            String email = "test@example.com";
            String password = "password123";
            
            when(userAccountMapper.insertUser(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount account = invocation.getArgument(0);
                account.setId(1L);
                return 1;
            });

            // When
            UserAccount result = userAuthService.register(email, password);

            // Then
            assertNotNull(result, "注册结果不应为空");
            assertEquals(email, result.getEmail(), "邮箱应正确设置");
            assertNull(result.getPhone(), "手机号应为空");
            assertEquals(UserRole.USER, result.getRole(), "角色应为普通用户");
        }
    }

    @Nested
    @DisplayName("registerMerchant 方法测试")
    class RegisterMerchantTests {

        @Test
        @DisplayName("应该成功注册商家账号")
        void should_registerMerchant_successfully() {
            // Given
            String phone = "13812345678";
            String password = "password123";
            String merchantName = "测试商家";
            
            when(userAccountMapper.insertUser(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount account = invocation.getArgument(0);
                account.setId(1L);
                return 1;
            });

            // When
            UserAccount result = userAuthService.registerMerchant(phone, password, merchantName);

            // Then
            assertNotNull(result, "注册结果不应为空");
            assertEquals(UserRole.MERCHANT, result.getRole(), "角色应为商家");
            assertEquals(merchantName, result.getMerchantName(), "商家名称应正确设置");
            assertFalse(result.getVerified(), "商家账号默认未审核");
        }
    }

    @Nested
    @DisplayName("login 方法测试")
    class LoginTests {

        @Test
        @DisplayName("应该成功登录")
        void should_login_successfully() {
            // Given
            String phoneOrEmail = "13812345678";
            String password = "password123";
            
            UserAccount account = new UserAccount();
            account.setId(1L);
            account.setPhone(phoneOrEmail);
            account.setRole(UserRole.USER);
            account.setEnabled(true);
            account.setPasswordHash(com.atguigu.common.utils.PasswordUtil.hashPassword(password));
            
            when(rateLimitService.isAccountLocked(phoneOrEmail)).thenReturn(false);
            when(userAccountMapper.selectByPhoneOrEmail(phoneOrEmail)).thenReturn(account);

            // When
            String token = userAuthService.login(phoneOrEmail, password);

            // Then
            assertNotNull(token, "Token不应为空");
            assertTrue(token.length() > 0, "Token长度应大于0");
            verify(rateLimitService).clearLoginFailure(phoneOrEmail);
        }

        @Test
        @DisplayName("应该拒绝被锁定的账号登录")
        void should_rejectLockedAccount() {
            // Given
            String phoneOrEmail = "13812345678";
            String password = "password123";
            
            when(rateLimitService.isAccountLocked(phoneOrEmail)).thenReturn(true);
            when(rateLimitService.getRemainingLockTime(phoneOrEmail)).thenReturn(300L);

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, 
                () -> userAuthService.login(phoneOrEmail, password));
            assertTrue(exception.getMessage().contains("账号已被锁定"));
        }

        @Test
        @DisplayName("应该拒绝不存在的用户登录")
        void should_rejectNonExistentUser() {
            // Given
            String phoneOrEmail = "13812345678";
            String password = "password123";
            
            when(rateLimitService.isAccountLocked(phoneOrEmail)).thenReturn(false);
            when(userAccountMapper.selectByPhoneOrEmail(phoneOrEmail)).thenReturn(null);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> userAuthService.login(phoneOrEmail, password));
            assertEquals("用户不存在", exception.getMessage());
        }

        @Test
        @DisplayName("应该拒绝被禁用的账号登录")
        void should_rejectDisabledAccount() {
            // Given
            String phoneOrEmail = "13812345678";
            String password = "password123";
            
            UserAccount account = new UserAccount();
            account.setId(1L);
            account.setPhone(phoneOrEmail);
            account.setEnabled(false);
            
            when(rateLimitService.isAccountLocked(phoneOrEmail)).thenReturn(false);
            when(userAccountMapper.selectByPhoneOrEmail(phoneOrEmail)).thenReturn(account);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> userAuthService.login(phoneOrEmail, password));
            assertEquals("用户账号已被禁用", exception.getMessage());
        }

        @Test
        @DisplayName("应该拒绝未审核的商家账号登录")
        void should_rejectUnverifiedMerchant() {
            // Given
            String phoneOrEmail = "merchant@example.com";
            String password = "password123";
            
            UserAccount account = new UserAccount();
            account.setId(1L);
            account.setEmail(phoneOrEmail);
            account.setRole(UserRole.MERCHANT);
            account.setEnabled(true);
            account.setVerified(false);
            
            when(rateLimitService.isAccountLocked(phoneOrEmail)).thenReturn(false);
            when(userAccountMapper.selectByPhoneOrEmail(phoneOrEmail)).thenReturn(account);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> userAuthService.login(phoneOrEmail, password));
            assertEquals("商家账号未通过审核", exception.getMessage());
        }

        @Test
        @DisplayName("应该拒绝错误密码登录")
        void should_rejectWrongPassword() {
            // Given
            String phoneOrEmail = "13812345678";
            String correctPassword = "password123";
            String wrongPassword = "wrongpassword";
            
            UserAccount account = new UserAccount();
            account.setId(1L);
            account.setPhone(phoneOrEmail);
            account.setRole(UserRole.USER);
            account.setEnabled(true);
            account.setPasswordHash(com.atguigu.common.utils.PasswordUtil.hashPassword(correctPassword));
            
            when(rateLimitService.isAccountLocked(phoneOrEmail)).thenReturn(false);
            when(userAccountMapper.selectByPhoneOrEmail(phoneOrEmail)).thenReturn(account);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> userAuthService.login(phoneOrEmail, wrongPassword));
            assertEquals("密码错误", exception.getMessage());
            verify(rateLimitService).recordLoginFailure(phoneOrEmail);
        }
    }

    @Nested
    @DisplayName("getCurrentUser 方法测试")
    class GetCurrentUserTests {

        @Test
        @DisplayName("应该返回用户信息")
        void should_returnUserInfo() {
            // Given
            Long userId = 1L;
            
            UserAccount account = new UserAccount();
            account.setId(userId);
            account.setPhone("13812345678");
            account.setNickName("测试用户");
            account.setRole(UserRole.USER);
            account.setEnabled(true);
            account.setVerified(true);
            
            when(userAccountMapper.selectById(userId)).thenReturn(account);

            // When
            UserInfo result = userAuthService.getCurrentUser(userId);

            // Then
            assertNotNull(result, "用户信息不应为空");
            assertEquals(userId, result.getId(), "用户ID应正确");
            assertEquals("测试用户", result.getNickName(), "昵称应正确");
            assertEquals(UserRole.USER, result.getRole(), "角色应正确");
        }

        @Test
        @DisplayName("应该返回null对于不存在的用户")
        void should_returnNullForNonExistentUser() {
            // Given
            Long userId = 999L;
            when(userAccountMapper.selectById(userId)).thenReturn(null);

            // When
            UserInfo result = userAuthService.getCurrentUser(userId);

            // Then
            assertNull(result, "不存在的用户应返回null");
        }
    }

    @Nested
    @DisplayName("updatePassword 方法测试")
    class UpdatePasswordTests {

        @Test
        @DisplayName("应该成功更新密码")
        void should_updatePassword_successfully() {
            // Given
            Long userId = 1L;
            String oldPassword = "oldPassword123";
            String newPassword = "newPassword456";
            
            UserAccount account = new UserAccount();
            account.setId(userId);
            account.setPasswordHash(com.atguigu.common.utils.PasswordUtil.hashPassword(oldPassword));
            
            when(userAccountMapper.selectById(userId)).thenReturn(account);
            when(userAccountMapper.updatePassword(eq(userId), anyString(), isNull())).thenReturn(1);

            // When
            userAuthService.updatePassword(userId, oldPassword, newPassword);

            // Then
            verify(userAccountMapper).updatePassword(eq(userId), anyString(), isNull());
        }

        @Test
        @DisplayName("应该拒绝不存在的用户更新密码")
        void should_rejectNonExistentUser() {
            // Given
            Long userId = 999L;
            when(userAccountMapper.selectById(userId)).thenReturn(null);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> userAuthService.updatePassword(userId, "old", "new"));
            assertEquals("用户不存在", exception.getMessage());
        }

        @Test
        @DisplayName("应该拒绝错误的旧密码")
        void should_rejectWrongOldPassword() {
            // Given
            Long userId = 1L;
            String correctOldPassword = "correctOldPassword";
            String wrongOldPassword = "wrongOldPassword";
            
            UserAccount account = new UserAccount();
            account.setId(userId);
            account.setPasswordHash(com.atguigu.common.utils.PasswordUtil.hashPassword(correctOldPassword));
            
            when(userAccountMapper.selectById(userId)).thenReturn(account);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> userAuthService.updatePassword(userId, wrongOldPassword, "newPassword"));
            assertEquals("旧密码错误", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("地址管理测试")
    class AddressTests {

        @Test
        @DisplayName("应该返回用户地址列表")
        void should_returnUserAddresses() {
            // Given
            Long userId = 1L;
            List<UserAddress> addresses = Arrays.asList(
                createTestAddress(1L, userId, "地址1"),
                createTestAddress(2L, userId, "地址2")
            );
            
            when(userAddressMapper.selectByUserId(userId)).thenReturn(addresses);

            // When
            List<UserAddress> result = userAuthService.listAddresses(userId);

            // Then
            assertNotNull(result, "地址列表不应为空");
            assertEquals(2, result.size(), "地址数量应正确");
        }

        @Test
        @DisplayName("应该成功保存新地址")
        void should_saveNewAddress_successfully() {
            // Given
            UserAddress address = createTestAddress(null, 1L, "新地址");
            
            when(userAddressMapper.insertAddress(any(UserAddress.class))).thenAnswer(invocation -> {
                UserAddress addr = invocation.getArgument(0);
                addr.setId(1L);
                return 1;
            });

            // When
            UserAddress result = userAuthService.saveOrUpdateAddress(address);

            // Then
            assertNotNull(result, "保存结果不应为空");
            assertNotNull(result.getId(), "ID应被设置");
            verify(userAddressMapper).insertAddress(any(UserAddress.class));
        }

        @Test
        @DisplayName("应该成功更新地址")
        void should_updateAddress_successfully() {
            // Given
            UserAddress address = createTestAddress(1L, 1L, "更新地址");
            
            when(userAddressMapper.updateAddress(
                eq(address.getId()), 
                eq(address.getUserId()), 
                eq(address.getConsignee()),
                eq(address.getPhone()), 
                eq(address.getProvince()), 
                eq(address.getCity()),
                eq(address.getDistrict()), 
                eq(address.getDetail())
            )).thenReturn(1);

            // When
            UserAddress result = userAuthService.saveOrUpdateAddress(address);

            // Then
            assertNotNull(result, "更新结果不应为空");
            verify(userAddressMapper).updateAddress(
                eq(address.getId()), 
                eq(address.getUserId()), 
                eq(address.getConsignee()),
                eq(address.getPhone()), 
                eq(address.getProvince()), 
                eq(address.getCity()),
                eq(address.getDistrict()), 
                eq(address.getDetail())
            );
        }

        @Test
        @DisplayName("应该成功删除地址")
        void should_deleteAddress_successfully() {
            // Given
            Long userId = 1L;
            Long addressId = 1L;
            
            when(userAddressMapper.deleteById(addressId, userId)).thenReturn(1);

            // When
            userAuthService.deleteAddress(userId, addressId);

            // Then
            verify(userAddressMapper).deleteById(addressId, userId);
        }
    }

    @Nested
    @DisplayName("管理员功能测试")
    class AdminFunctionTests {

        @Test
        @DisplayName("应该成功更新用户状态")
        void should_updateUserStatus_successfully() {
            // Given
            Long userId = 1L;
            boolean enabled = false;
            
            when(userAccountMapper.updateStatus(userId, enabled)).thenReturn(1);

            // When
            userAuthService.updateUserStatus(userId, enabled);

            // Then
            verify(userAccountMapper).updateStatus(userId, enabled);
        }

        @Test
        @DisplayName("应该成功更新用户角色")
        void should_updateUserRole_successfully() {
            // Given
            Long userId = 1L;
            UserRole role = UserRole.ADMIN;
            
            when(userAccountMapper.updateRole(userId, role.name())).thenReturn(1);

            // When
            userAuthService.updateUserRole(userId, role);

            // Then
            verify(userAccountMapper).updateRole(userId, role.name());
        }

        @Test
        @DisplayName("应该成功删除用户")
        void should_deleteUser_successfully() {
            // Given
            Long userId = 1L;
            
            when(userAccountMapper.deleteById(userId)).thenReturn(1);

            // When
            userAuthService.deleteUser(userId);

            // Then
            verify(userAccountMapper).deleteById(userId);
        }

        @Test
        @DisplayName("应该批量更新用户状态")
        void should_batchUpdateUserStatus_successfully() {
            // Given
            Map<Long, Boolean> statusMap = new HashMap<>();
            statusMap.put(1L, true);
            statusMap.put(2L, false);
            
            when(userAccountMapper.updateStatus(1L, true)).thenReturn(1);
            when(userAccountMapper.updateStatus(2L, false)).thenReturn(1);

            // When
            Map<Long, Boolean> result = userAuthService.batchUpdateUserStatus(statusMap);

            // Then
            assertEquals(2, result.size(), "结果数量应正确");
            assertTrue(result.get(1L), "用户1更新应成功");
            assertTrue(result.get(2L), "用户2更新应成功");
        }

        @Test
        @DisplayName("应该批量更新用户角色")
        void should_batchUpdateUserRole_successfully() {
            // Given
            Map<Long, UserRole> roleMap = new HashMap<>();
            roleMap.put(1L, UserRole.ADMIN);
            roleMap.put(2L, UserRole.USER);
            
            when(userAccountMapper.updateRole(1L, UserRole.ADMIN.name())).thenReturn(1);
            when(userAccountMapper.updateRole(2L, UserRole.USER.name())).thenReturn(1);

            // When
            Map<Long, Boolean> result = userAuthService.batchUpdateUserRole(roleMap);

            // Then
            assertEquals(2, result.size(), "结果数量应正确");
            assertTrue(result.get(1L), "用户1更新应成功");
            assertTrue(result.get(2L), "用户2更新应成功");
        }

        @Test
        @DisplayName("应该批量删除用户")
        void should_batchDeleteUsers_successfully() {
            // Given
            List<Long> userIds = Arrays.asList(1L, 2L, 3L);
            
            when(userAccountMapper.deleteById(1L)).thenReturn(1);
            when(userAccountMapper.deleteById(2L)).thenReturn(1);
            when(userAccountMapper.deleteById(3L)).thenReturn(0);

            // When
            Map<Long, Boolean> result = userAuthService.batchDeleteUsers(userIds);

            // Then
            assertEquals(3, result.size(), "结果数量应正确");
            assertTrue(result.get(1L), "用户1删除应成功");
            assertTrue(result.get(2L), "用户2删除应成功");
            assertFalse(result.get(3L), "用户3删除应失败");
        }
    }

    @Nested
    @DisplayName("商家管理测试")
    class MerchantTests {

        @Test
        @DisplayName("应该返回商家信息")
        void should_returnMerchantInfo() {
            // Given
            Long merchantId = 1L;
            
            UserAccount merchant = new UserAccount();
            merchant.setId(merchantId);
            merchant.setRole(UserRole.MERCHANT);
            merchant.setMerchantName("测试商家");
            
            when(userAccountMapper.selectById(merchantId)).thenReturn(merchant);

            // When
            UserAccount result = userAuthService.getMerchantById(merchantId);

            // Then
            assertNotNull(result, "商家信息不应为空");
            assertEquals(UserRole.MERCHANT, result.getRole(), "角色应为商家");
        }

        @Test
        @DisplayName("应该返回null对于非商家用户")
        void should_returnNullForNonMerchant() {
            // Given
            Long userId = 1L;
            
            UserAccount user = new UserAccount();
            user.setId(userId);
            user.setRole(UserRole.USER);
            
            when(userAccountMapper.selectById(userId)).thenReturn(user);

            // When
            UserAccount result = userAuthService.getMerchantById(userId);

            // Then
            assertNull(result, "非商家用户应返回null");
        }

        @Test
        @DisplayName("应该成功审核商家")
        void should_verifyMerchant_successfully() {
            // Given
            Long merchantId = 1L;
            boolean approved = true;
            
            when(userAccountMapper.updateMerchantVerify(merchantId, approved, approved)).thenReturn(1);

            // When
            userAuthService.verifyMerchant(merchantId, approved);

            // Then
            verify(userAccountMapper).updateMerchantVerify(merchantId, approved, approved);
        }
    }

    // ========== 辅助方法 ==========

    private UserAddress createTestAddress(Long id, Long userId, String detail) {
        UserAddress address = new UserAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setConsignee("测试收货人");
        address.setPhone("13812345678");
        address.setProvince("北京市");
        address.setCity("北京市");
        address.setDistrict("朝阳区");
        address.setDetail(detail);
        return address;
    }
}
